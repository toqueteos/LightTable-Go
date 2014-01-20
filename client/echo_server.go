package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"sync"
)

const (
	LogFile    = "echo_server.log"
	ClientHost = "127.0.0.1"
	ClientName = "ltgo"
	ClientType = "go"
)

func init() {
	f, err := os.OpenFile(LogFile, os.O_CREATE|os.O_TRUNC, 0666)
	if err != nil {
		log.Panicf("Can't create/open logfile %q.\n", LogFile)
	} else {
		log.SetOutput(f)
	}

	signal.Notify(chSig, os.Interrupt, os.Kill)

	go HandleSignals()
}

// Only one client. Client reads and writes JSON-encoded messages to LightTable.
var client net.Conn
var stop bool
var wg sync.WaitGroup
var defaultId int

func main() {
	var Addr = fmt.Sprintf("%s:%s", ClientHost, os.Args[1])

	var err error
	client, err = net.Dial("tcp", Addr)
	if err != nil {
		log.Println(err)
		return
	}
	log.Printf("Connected to", Addr)

	Start()

	Handle()
}

// +++++ +++++ +++++ +++++ +++++ +++++ +++++ +++++ +++++ +++++ +++++ +++++ +++++

// First message sent to LighTable is formatted as:
// {
//     "name": "process-name",
//     "client-id": 123,
//     "dir": "/path/to/pwd",
//     "commands": ["editor.eval.mylanguage"],
//     "type": "mylanguage"
// }
//
// Note: `client-id` must be an integer.
type clientInfo struct {
	Name string `json:"name"`
	Cid  int    `json:"client-id"`
	Dir  string `json:"dir"`
	Cmd  string `json:"commands"`
	Type string `json:"type"`
}

func Start() {
	// Forward back some basic info to LightTable to let it know client is
	// working.
	var clientId, _ = strconv.Atoi(os.Args[2])
	var pwd, _ = os.Getwd()

	msg, _ := json.Marshal(clientInfo{
		Name: ClientName,
		Cid:  clientId,
		Dir:  pwd,
		Cmd:  "editor.eval.go",
		Type: ClientType,
	})

	SendRaw(msg)

	log.Printf("Sent starting message to LightTable: %q", msg)
}

func Print(cid int, s string) {
	Send(cid, "editor.eval.go.print", Info{
		"msg":  s,
		"file": ClientName,
	})
}

func SendRaw(b []byte) {
	buf := bufio.NewWriter(client)
	buf.Write(b)
	buf.WriteString("\n")
	buf.Flush()
}

func Send(cid int, cmd string, i Info) {
	var m Message
	m.Cid = cid
	m.Cmd = cmd
	m.Info = i

	b, _ := json.Marshal(m.ToJSON())
	SendRaw(b)
}

func Handle() {
	buf := bufio.NewReader(client)

	for !stop {
		line, err := buf.ReadString('\n')
		if err != nil {
			continue
		}

		var m = NewMessage(line)

		switch m.Cmd {
		case "client.close":
		case "client.cancel-all":
			Shutdown()
		case "editor.eval.go":
			// Echo message back
			Send(m.Cid, "editor.eval.go.result", Info{
				"result": m.Info["code"],
				"meta":   m.Info["meta"],
			})

			//wg.Add(1)
			//go NewEvalClient(m)
		}
	}
}

func Shutdown() {
	stop = false
	wg.Wait()
}

type Message struct {
	Cid  int
	Cmd  string
	Info Info
}

type Info map[string]interface{}

// NewMessage parses LightTable's incoming JSON messages, encoded as lists, and
// parses their content into a type-safe Message.
//
// Lists always look like this: `[int,string,object]`
func NewMessage(s string) *Message {
	// 1. Remove start and end list brackets.
	// 2. Split by commas in 3 blocks.
	// 3. json.Unmarshal each part.
	var parts = strings.SplitN(s[1:len(s)-1], ",", 3)

	var m Message
	var err error

	// Client id
	err = json.Unmarshal([]byte(parts[0]), &m.Cid)
	if err != nil {
		return nil
	}
	// Command
	err = json.Unmarshal([]byte(parts[1]), &m.Cmd)
	if err != nil {
		return nil
	}
	// Info
	err = json.Unmarshal([]byte(parts[2]), &m.Info)
	if err != nil {
		return nil
	}

	return &m
}

func (m Message) ToJSON() string {
	var err error
	var jsonCid, jsonCmd, jsonInfo []byte

	jsonCid, err = json.Marshal(m.Cid)
	if err != nil {
		return ""
	}
	jsonCmd, err = json.Marshal(m.Cmd)
	if err != nil {
		return ""
	}
	jsonInfo, err = json.Marshal(m.Info)
	if err != nil {
		return ""
	}

	return fmt.Sprintf("[%s,%s,%s]\n", jsonCid, jsonCmd, jsonInfo)
}

func NewEvalClient(m *Message) {
	defer wg.Done()

	Print(m.Cid, fmt.Sprintf("Worker#%d done.", m.Cid))
}

// Set up channel on which to send signal notifications.
// We must use a buffered channel or risk missing the signal
// if we're not ready to receive when the signal is sent.
var chSig = make(chan os.Signal, 1)

func HandleSignals() {
	// Block until a signal is received.
	//s := <-chSig
	<-chSig

	Shutdown()
	//fmt.Println("Got signal:", s)
}
