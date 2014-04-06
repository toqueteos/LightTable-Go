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

// Takes a string like strings.Replace(m.Info.Code, "\\n", "\n", -1) and isolates the strings.Replace part.
// For getting documentation. Is there a better way to do this?
func GetQuerySubject(line string, cursorPos int) []string {
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

    fmt.Printf( "Doc query: %v\n", query )

	return query
}

func SearchDocs( query []string ) *Doc {

    fmt.Printf( "query %v\n", query)

    cmd := exec.Command("godoc", query... )

    //in := strings.NewReader(query)
    var out bytes.Buffer
    var stderr bytes.Buffer
    //cmd.Stdin = in
    cmd.Stdout = &out
    cmd.Stderr = &stderr

    err := cmd.Run()
    if err != nil {
        log.Println("[Searchdocs] Couldn't exec godoc.")
        fmt.Printf( "[Searchdocs] Couldn't exec godoc. %s\n", stderr.String() )
        return nil
    }



    //TODO: Replace logs with printf so that the light table user can see them.

    result_lines := strings.Split( out.String(), "\n" )

    var namespace string
    var name string
    //var args string
    var docs string

    state := "PACKAGE"

    for i := 0; i < len( result_lines ); i++ {
        line := result_lines[i]
        if state == "PACKAGE" && strings.Contains( line, "package" ) {
            // Get the package name
            splitline := strings.Split( line, " " )
            if len( splitline ) > 1 {
                namespace = splitline[1]
            }
            state = "FUNC"
            continue
        }

        if state == "FUNC" {
            if strings.Contains( line, "FUNCTIONS" ) {
                i = i + 2
                if i < len( result_lines ) {
                    name = result_lines[i]

                    var docbuffer bytes.Buffer

                    i += 1
                    for i < len( result_lines ) && result_lines[i] != "" {
                        docbuffer.WriteString( strings.TrimSpace( result_lines[i] ) )
                        docbuffer.WriteString( "\n" )
                        i++
                    }
                    docs = docbuffer.String()
                }
                break
            }
        }
    }

    doc := &Doc{}

    doc.Name = name
    doc.Ns = namespace
    doc.Doc = docs

    fmt.Printf( "Docs: %+v", doc )

    /*
    	Name string `json:"name"`
	Ns   string `json:"ns"`
	Args string `json:"args"`
	Doc  string `json:"doc"`
	Loc  *Pos   `json:"loc"`
    */
    return doc
}

// Dochandler sends responses to "editor.doc.go" requests.
func DocHandler(m *Message) {
	defer wg.Done()

	//TODO Find out the document encoding

	var i Info
	var doc *Doc

    /*
	doc.Name = "Name"
	//doc.Ns = "Namespace"
	//doc.Args = "Args"
	doc.Doc = "Docs"
	doc.Loc = m.Info.Pos

	i.Doc = &doc
*/

	var code string = strings.Replace(m.Info.Code, "\\n", "\n", -1)
	var pos *Pos = m.Info.Pos
	//var path string = m.Info.Path
	var splitCode []string = strings.Split(code, "\n")
	//var in *strings.Reader
	//var out bytes.Buffer
	var line string
	var linePos int = pos.Ch
	//var querySubject string

	//var unprocessed_hints []string
	//var hints []string

	if len(splitCode) <= pos.Line {
		return //TODO Send error?
	}

	line = splitCode[pos.Line]

	// Set the cursor to the end of the line if it's beyond that
	if len(line) < linePos {
		linePos = len(line)
	}

    query := GetQuerySubject(line, linePos)
    doc = SearchDocs( query )
    doc.Loc = pos

    i.Doc = doc

	/*


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
	*/
	fmt.Printf("Getting docs")
	Send(m.Cid, "editor.go.doc", i)
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

type Doc struct {
	Name string `json:"name"`
	Ns   string `json:"ns"`
	Args string `json:"args"`
	Doc  string `json:"doc"`
	Loc  *Pos   `json:"loc"`
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
