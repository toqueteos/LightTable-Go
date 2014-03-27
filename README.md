## Go for Light Table

An unofficial Go language plugin for Light Table.

I'm not a Clojure expert so don't expect too much fanciness here, changes are welcome.

### Features

1. `go fmt` integration, via command and after each save.
2. Dummy eval `Ctrl+Enter` (check client/echo_server.go) instead of code evaluation. Needs integration with MarGo (like [GoSublime](https://github.com/DisposaBoy/GoSublime)).
3. `go build` integration, via command. Builds in current file directory
4. `go run` integration, via command.
5. Package sensitive autocompletion, via [gocode](https://github.com/nsf/gocode). Gocode must be installed on your system & available on your path for this to work.

### Credits

Thanks to Rafe Rosen and Mads:

https://groups.google.com/forum/#!topic/light-table-discussion/Dg0ldzLx4F4

https://groups.google.com/forum/#!topic/light-table-discussion/ow_vhto7uDY

### License

Copyright (C) 2014 Carlos Cobo <toqueteos@gmail.com>.

Distributed under the MIT license, see LICENSE.md for the full text.
