package main

import (
    "bufio"
    "bytes"
    "encoding/json"
    "fmt"
    "io/ioutil"
    "log"
    "net"
    "os"
    "os/exec"
    "os/signal"
    "strconv"
    "strings"
    "sync"

    "go/build"
    "go/token"
    "go/parser"
    "go/doc"
    "errors"
)

const (
    LogEnabled = false
    LogFile    = `echo_server.log`
    ClientHost = "127.0.0.1"
    ClientName = "LightTable-Go"
    ClientType = "go"
)

func init() {
    signal.Notify(chSig, os.Interrupt, os.Kill)

    go HandleSignals()
}

// Only one client. Client reads and writes JSON-encoded messages to LightTable.
var client net.Conn
var stop bool // Same as: var stop = false
var wg sync.WaitGroup
var gocodeActive bool //false if we're unable to start gocode.

func main() {
    var err error

    // Setup logging
    if !LogEnabled {
        log.SetOutput(ioutil.Discard)
    } else {
        var f *os.File
        f, err = os.OpenFile(LogFile, os.O_CREATE|os.O_TRUNC, 0666)
        if err != nil {
            log.Panicf("Can't create/open logfile %q.\n", LogFile)
        } else {
            log.SetOutput(f)
        }
        defer f.Close()
    }

    var Addr = fmt.Sprintf("%s:%s", ClientHost, os.Args[1])
    client, err = net.Dial("tcp", Addr)
    if err != nil {
        log.Println(err)
        return
    }
    log.Println("Connected to", Addr)
    log.Println("Args:", os.Args)

    Start()

    Handle()
}

// First message sent to LighTable is formatted as:
// {
//     "name": "process-name",
//     "client-id": 123,
//     "dir": "/path/to/pwd",
//     "commands": ["editor.eval.mylanguage"],
//     "type": "mylanguage"
// }
//
// Note: `client-id` MUST be an integer.
type clientInfo struct {
    Name string `json:"name"`
    Cid  int    `json:"client-id"`
    Dir  string `json:"dir"`
    Cmd  string `json:"commands"`
    Type string `json:"type"`
}

// Start sends sends back to LightTable the same client-id it received
// (os.Args[2]) and other info as well such as: client name, client path,
// commands it will operate? (unconfirmed), ..
func Start() {
    var err error

    var clientId int
    clientId, err = strconv.Atoi(os.Args[2])
    if err != nil {
        log.Fatalf("[Start] Couldn't parse client-id, received %s. Error: %s\n", os.Args[2], err)
    }

    var pwd string
    pwd, err = os.Getwd()
    if err != nil {
        log.Fatalln("[Start] Couldn't get working directory. Error:", err)
    }

    var msg []byte
    msg, err = json.Marshal(clientInfo{
        Name: ClientName,
        Cid:  clientId,
        Dir:  pwd,
        Cmd:  "editor.eval.go",
        Type: ClientType,
    })
    if err != nil {
        log.Fatalln("[Start] json.Marshal error:", err)
    }

    // Activate the autocomplete server
    var gocode *exec.Cmd
    gocode = exec.Command("gocode")
    gocodeActive = true
    if gocode.Start() != nil {
        log.Println("Unable to start gocode. Autocompletion is disabled.")
        gocodeActive = false
    }

    log.Printf("Sending starting message to LightTable: %s", msg)
    // Tell Light Table we're connected
    fmt.Printf("connected")

    buf := bufio.NewWriter(client)
    buf.Write(msg)
    buf.WriteString("\n")
    buf.Flush()
}

func Stop() {
    stop = true
    wg.Wait()

    // Shut down gocode
    if gocodeActive {
        if exec.Command("gocode", "close").Run() != nil {
            log.Fatalln("[Stop] Unable to close gocode.")
        }
    }
    client.Close()

    log.Println("Stop!")
    os.Exit(0)
}

func Send(cid int, cmd string, i Info) {
    var m Message
    m.Cid = cid
    m.Cmd = cmd
    m.Info = i

    var mj = m.ToJSON()
    log.Printf("Send %s", mj)

    buf := bufio.NewWriter(client)
    buf.WriteString(mj)
    buf.WriteString("\n")
    buf.Flush()
}

func Handle() {
    buf := bufio.NewReader(client)

    for !stop {
        line, err := buf.ReadString('\n')
        if err != nil {
            continue
        }

        var m = NewMessage(line)
        if m == nil {
            continue
        }

        switch m.Cmd {
        case "client.close", "client.cancel-all":
            Stop()

        case "editor.eval.go":
            log.Println("[Handle] Got Message", m)

            wg.Add(1)
            go EvalHandler(m)

        case "editor.go.hints":
            log.Println("[Handle] Got Autocomplete message", m)
            if gocodeActive {
                wg.Add(1)
                go AutoCompleteHandler(m)
            } else {
                log.Println("[Handle] gocode not running, ignoring autocomplete", m)
            }

        case "editor.go.doc":
			log.Println("[Handle] Got doc message", m)
			wg.Add(1)
			go DocHandler(m)
        }
    }
}

// EvalHandler sends responses to "editor.eval.go" requests.
func EvalHandler(m *Message) {
    defer wg.Done()

    var i Info

    // Echo message back
    if m.Info.Code == "" {
        m.Info.Code = "Nothing selected. Line echo not implemented yet."
    }

    i.Result = m.Info.Code
    i.Pos = m.Info.Pos

    Send(m.Cid, "editor.eval.go.result", i)
}

// AutoCompleteHandler sends responses to "editor.autocomplete.go" requests.
func AutoCompleteHandler(m *Message) {
    defer wg.Done()

    var code string = strings.Replace(m.Info.Code, "\\n", "\n", -1)
    var pos *Pos = m.Info.Pos
    var path string = m.Info.Path
    var splitCode []string = strings.Split(strings.Replace(code, "\\n", "\n", -1), "\n")
    var byteCount int = 0
    var in *strings.Reader
    var out bytes.Buffer
    var i Info
    var unprocessed_hints []string
    var hints []string

    for lineNum, line := range splitCode {
        if lineNum == pos.Line {
            byteCount = byteCount + pos.Ch
            break
        }
        byteCount = byteCount + len(line) + 1 // +1 for the newline that strings.Split removed
    }

    // If the incoming source code is unicode, should be c%d below.
    cmd := exec.Command("gocode", "-f=csv", "autocomplete", path, fmt.Sprintf("%d", byteCount))

    in = strings.NewReader(code)
    cmd.Stdin = in
    cmd.Stdout = &out

    err := cmd.Run()
    if err != nil {
        log.Println("[AutoCompleteHandler] Couldn't exec gocode.")
        return
    }

    i.Result = m.Info.Code
    i.Pos = m.Info.Pos

    // Parse the autocomplete results
    unprocessed_hints = strings.Split(out.String(), "\n")
    hints = make([]string, len(unprocessed_hints))

    for i, hint := range unprocessed_hints {
        splitHints := strings.Split(hint, ",")
        if len(splitHints) >= 5 {
            hints[i] = splitHints[2] //splitHints[4] has the function signature data, but light table can't use that yet.
        }
    }

    i.Hints = hints

    Send(m.Cid, "editor.go.hints.result", i)
}

// Dochandler sends responses to "editor.doc.go" requests.
func DocHandler(m *Message) {
	defer wg.Done()
	//TODO Find out the document encoding

	var i Info

	var code string = strings.Replace(m.Info.Code, "\\n", "\n", -1)
	var pos *Pos = m.Info.Pos

	var splitCode []string = strings.Split(code, "\n")

	var line string
	var linePos int = pos.Ch

	if len(splitCode) <= pos.Line {
		return //TODO Send error?
	}

	line = splitCode[pos.Line]

	// Set the cursor to the end of the line if it's beyond that
	if len(line) < linePos {
		linePos = len(line)
	}

    pkg_name, func_name := GetQuerySubject(line, linePos)
    if docs, err := searchDocs( pkg_name, func_name ); err != nil {
        return // Couldn't get docs
    } else {
        docs.Loc = pos // Why is this a problem?
        i.Doc = docs

        fmt.Printf("Getting docs")
        Send(m.Cid, "editor.go.doc", i)
    }
}

type Message struct {
    Cid  int
    Cmd  string
    Info Info
}

type Info struct {
    Code       string   `json:"code,omitempty"`
    Doc        *Doc     `json:"doc,omitempty"`
    LineEnding string   `json:"line-ending,omitempty"`
    Meta       *Meta    `json:"meta,omitempty"`
    Mime       string   `json:"mime,omitempty"`
    Name       string   `json:"name,omitempty"`
    Path       string   `json:"path,omitempty"`
    Pos        *Pos     `json:"pos,omitempty"`
    Tags       []string `json:"tags,omitempty"`
    TypeName   string   `json:"type-name,omitempty"`
    Result     string   `json:"result,omitempty"`
    Hints      []string `json:"hints,omitempty"`
    // Msg      string   `json:"msg,omitempty"`
    // File     string   `json:"file,omitempty"`
}

type Meta struct {
    End   int `json:"end"`
    Start int `json:"start"`
}

type Pos struct {
    Ch   int `json:"ch"`
    Line int `json:"line"`
}

// NewMessage parses LightTable's incoming JSON messages, encoded as lists, and
// parses their content into a type-safe Message.
//
// Lists always look like this: `[int,string,object]`
func NewMessage(s string) *Message {
    const NumParts = 3

    // Remove newline at end then first/last bracket
    s = strings.TrimSpace(s)
    s = s[1 : len(s)-1] //s = strings.Trim(s, "[]")

    var parts = strings.SplitN(s, ",", NumParts)
    log.Printf("[NewMessage] %d/%d parts: %s", len(parts), NumParts, parts)

    var m Message
    var field = []interface{}{
        &m.Cid, &m.Cmd, &m.Info,
    }

    // json.Unmarshal each part.
    for index := 0; index < NumParts; index++ {
        var err error

        err = json.Unmarshal([]byte(parts[index]), field[index])
        if err != nil {
            log.Printf("[NewMessage] json.Unmarshal parts[%d] failed: %s", index, err)
            return nil
        }
    }

    return &m
}

func (m Message) ToJSON() string {
    var err error
    var jsonCid, jsonCmd, jsonInfo []byte

    jsonCid, err = json.Marshal(m.Cid)
    if err != nil {
        log.Printf("[Message.ToJSON] json.Marshal of %q failed.", "m.Cid")
        return ""
    }
    jsonCmd, err = json.Marshal(m.Cmd)
    if err != nil {
        log.Printf("[Message.ToJSON] json.Marshal of %q failed.", "m.Cmd")
        return ""
    }
    jsonInfo, err = json.Marshal(m.Info)
    if err != nil {
        log.Printf("[Message.ToJSON] json.Marshal of %q failed.", "m.Info")
        return ""
    }

    return fmt.Sprintf("[%s,%s,%s]", jsonCid, jsonCmd, jsonInfo)
}

// Set up channel on which to send signal notifications.
// We must use a buffered channel or risk missing the signal
// if we're not ready to receive when the signal is sent.
var chSig = make(chan os.Signal, 1)

func HandleSignals() {
    // Block until a signal is received.
    //s := <-chSig
    <-chSig

    Stop()
    //fmt.Println("Got signal:", s)
}

/******************
 * Doc stuff
 ******************/

type Doc struct {
	Name string `json:"name"`
	Ns   string `json:"ns"` // Package - named Ns because that's what Light Table wants.
    Type string `json:"type"`
	Args string `json:"args"`
	Doc  string `json:"doc"`
	Loc  *Pos   `json:"loc"`
}

// Takes a string like strings.Replace(m.Info.Code, "\\n", "\n", -1) and isolates the package name and function.
// For getting documentation. TODO: Figure out a better approach
func GetQuerySubject(line string, cursorPos int) (string, string) {
	querySubjectStart := cursorPos
	querySubjectEnd := cursorPos

	var queryDelims string = "\t()[]&|!#/*- "

	for querySubjectStart > 0 && strings.IndexByte(queryDelims, line[querySubjectStart]) == -1 {
		querySubjectStart--
	}

	for querySubjectEnd < len(line) && strings.IndexByte(queryDelims, line[querySubjectEnd]) == -1 {
		querySubjectEnd++
	}

    query := strings.Split( strings.TrimSpace( line[querySubjectStart:querySubjectEnd] ), "." )

    if len( query ) != 2 {
        fmt.Printf( "Error with query. %v\n", query )
        return "", ""
    }

	return query[0], query[1]
}

// Gets a doc.Package struct from the package name. Returns success or failure
func getDocPackage( package_name string ) ( *doc.Package, error ) {
    // Import the pkg name to get a list of its source files. TODO - gopath?
    fset := token.NewFileSet()

    if pkg, err := build.Default.Import(package_name, build.Default.GOROOT, build.AllowBinary); err != nil {
        //error
        return nil, errors.New( "Package not found")
    } else if pkg, err = build.Default.Import(package_name, build.Default.GOPATH, build.AllowBinary); err != nil {
        //error
        return nil, errors.New( "Package not found")
    } else {
        if parsed_dir, err := parser.ParseDir( fset, pkg.Dir, nil, parser.ParseComments ); err != nil {
            return nil, err
        } else {
            for _, val := range parsed_dir { // This is a bad solution. Loops over the packages found and ignores their names.
                return doc.New( val, pkg.ImportPath, 0 ), nil
            }

            return nil, errors.New( "Package not found")
        }
    }
}

func searchDocs( package_name string, func_name string ) ( *Doc, error ) {
    if pkg, err := getDocPackage( package_name ); err == nil {
        if func_name != "" {
            for _, v := range pkg.Funcs {
                if v.Name == func_name {
                    doc := &Doc{}
                    doc.Ns = package_name
                    doc.Name = func_name
                    doc.Doc = v.Doc
                    return doc, nil
                }
            }
        }
        return nil, errors.New( "Couldn't get docs for " + package_name + " " + func_name )
    } else {
        return nil, errors.New( "Couldn't get docs for " + package_name + " " + func_name )
    }
}
