## Go for Light Table

An unofficial Go language plugin for Light Table.

We aren't Clojure experts so don't expect too much fanciness here, changes are welcome.

### Features

1. `go fmt` integration, via command or after each save.
2. Dummy eval `Ctrl+Enter` (check client/echo_server.go) instead of code evaluation. Needs integration with MarGo (like [GoSublime](https://github.com/DisposaBoy/GoSublime)).
3. `go test` `go run` `go build` commands which can be re-bound.
4. Plugin documentation (`Ctrl+Space` + "Go: Show plugin documentation")
5. Package sensitive autocompletion, via [gocode](https://github.com/nsf/gocode). Gocode must be installed on your system & available on your path for this to work.

### Installation

The version of this plugin in the plugin manager for Light Table may be out of date. If you want the latest version, clone this repository into your Light Table plugins folder with the name "LightTable-Go".

### Configuration

To configure this plugin, just attach the appropriate behaviors to :editor.go in your user.behaviors file. Example:

    :editor.go [(:lt.plugins.go/change-gofmt-command "gofmt -w=true")

             (:lt.plugins.go/change-gobuild-command "go build -o")

             (:lt.plugins.go/change-gorun-command "go run")

             (:lt.plugins.go/change-gotest-command "go test")]

The example given above shows the default values for the available commands.

Additional behaviors for :editor.go:

`(:lt.objs.editor/tab-settings use-tab tab-size spaces-per-indent)` -- Part of Light Table. In case you don't like Go's default style.

`:lt.plugins.go/fmt-on-save` -- Runs gofmt whenever you save & replaces the editor contents with the formatted code.

### Autocomplete

For autocomplete to work, [Gocode](https://github.com/nsf/gocode) must be installed on your system & available on your path.

### Inline docs

Basic inline documentation (default: ctrl-d) is available for package level functions. Does not currently work for struct methods or packages with a /
in their name (net/http, for example), or show arguments for the functions. It's all on the to-do list.

### Credits

Thanks to Rafe Rosen and Mads:

https://groups.google.com/forum/#!topic/light-table-discussion/Dg0ldzLx4F4

https://groups.google.com/forum/#!topic/light-table-discussion/ow_vhto7uDY

### License

Copyright (C) 2014 Carlos Cobo <toqueteos@gmail.com>.

Distributed under the MIT license, see LICENSE.md for the full text.
