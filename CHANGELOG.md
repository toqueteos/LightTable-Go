## 0.1.3

Bugfixes

 - Go-fmt now works when used from the sidebar
 - Go-test prints output to console for failed tests

## 0.1.2

Features

 - Added basic inline documentation

## 0.1.1

Bugfixes:

 - Disabled logging in client until a better log file location is found. It causes the
 client to die if it can't write to the log file.
 - Added environment variables to exec call. Should fix issues with systems not finding the go commands.

Other:

 - Added installation instructions to README.md


## 0.1.0

Features:

 - Added behaviors for rebinding go commands
 - Added command for running `go test`
 - Added command to view plugin readme

Bugfixes:

 - Removed a bunch of unneeded console\logs
 - Fixed plugin location detection

Other:

 - Added changelog
 - Decided the new behaviors and commands added since 0.0.7 were enough to warrant a minor version increment. :-)
