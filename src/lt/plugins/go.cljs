(ns lt.plugins.go
  (:require [lt.object :as object]
            [lt.objs.clients :as clients]
            [lt.objs.clients.tcp :as tcp]
            [lt.objs.command :as cmd]
            [lt.objs.console :as console]
            [lt.objs.editor :as ed]
            [lt.objs.editor.pool :as pool]
            [lt.objs.eval :as eval]
            [lt.objs.files :as files]
            [lt.objs.notifos :as notifos]
            [lt.objs.plugins :as plugins]
            [lt.objs.popup :as popup]
            [lt.objs.proc :as proc]
            [lt.objs.tabs :as tabs]
            [lt.plugins.auto-complete :as auto-complete])
  (:require-macros [lt.macros :refer [behavior]]))

;;****************************************************
;; Initialization
;;****************************************************

;; Declares a new object called "go-lang" and also lets you assign things to it;
;; in your case a set of with just a tag.
(object/object* ::go-lang
                :tags #{:go.lang}
                :behaviors []
                :settings {})

;; Create object ::go-lang
(def go (object/create ::go-lang))

;; Only for people developing this plugin. Setting to true will print stdout from the client to the console.
(def debug-mode true)

(defn get-setting
  [setting]
  (get-in @go [:settings setting]))

(defn set-setting
  [setting value]
  (object/update! go [:settings setting] (fn [] value )))

;;****************************************************
;; Behaviours the user might want to set
;;****************************************************

(behavior ::change-gofmt-command
          :triggers #{:object.instant}
          :for ::go-lang
          :desc "Go plugin: Assign gofmt command"
          :params [{:label "Default: 'gofmt -w=true'" :type :keyword}]
          :type :user
          :reaction (fn [this new-cmd]
                      (set-setting :go-fmt-command new-cmd)))

(behavior ::fmt-on-save
          :triggers #{:save}
          :for ::go-lang
          :desc "Go plugin: Run gofmt on save"
          :type :user
          :reaction (fn [editor]
                      (let [path (-> @editor :info :path)]
                        (gofmt path))))

(behavior ::change-gorun-command
          :triggers #{:object.instant}
          :for ::go-lang
          :desc "Go plugin: Assign go run command"
          :params [{:label "Default: 'go run'" :type :keyword}]
          :type :user
          :reaction (fn [this new-cmd]
                      (set-setting :go-run-command new-cmd)))

(behavior ::change-gobuild-command
          :triggers #{:object.instant}
          :for ::go-lang
          :desc "Go plugin: Assign go build command"
          :params [{:label "Default: 'go build -o'" :type :keyword}]
          :type :user
          :reaction (fn [this new-cmd]
                      (set-setting :go-build-command new-cmd)))

(behavior ::change-gotest-command
          :triggers #{:object.instant}
          :for ::go-lang
          :desc "Go plugin: Assign go test command"
          :params [{:label "Default: 'go test'" :type :keyword}]
          :type :user
          :reaction (fn [this new-cmd]
                      (set-setting :go-test-command new-cmd)))

(behavior ::show-console-on-go-command
          :triggers #{:object.instant}
          :for ::go-lang
          :desc "Go plugin: Show console for go command output"
          :params [{:label "Default: false"}]
          :type :user
          :reaction (fn [this show]
                      (set-setting :go-show-console show)))

;;****************************************************
;; Connections
;;****************************************************

;; Attempts to establish a connection with the Go client.
(defn try-connect [{:keys [info]}]
  (let [path (:path info)
        client (clients/client! :go.client)
        obj (object/create ::connecting-notifier client)]
    (object/add-tags client [:tcp.client])
    (notifos/working "Connecting to go client")
    (proc/exec {:command "go"
                :args ["run" client-path tcp/port (clients/->id client)]
                :cwd "."
                :env {}
                :obj obj})
     client))

;; Background-process client behaviours.
(behavior ::on-out
          :triggers #{:proc.out}
          :reaction (fn [this data]
                      (let [out (.toString data)]
                        (when debug-mode (console/log out))
                        (object/update! this [:buffer] str out)
                        (when (> (.indexOf out "connected") -1)
                          (do
                            (notifos/done-working)
                            (object/merge! this {:connected true}))))))

(behavior ::on-error
          :triggers #{:proc.error}
          :reaction (fn [this data]
                      (let [out (.toString data)]
                        (console/log out)
                        (when-not (> (.indexOf (:buffer @this) "connected") -1)
                          (object/update! this [:buffer] str data)))))

(behavior ::on-exit
          :triggers #{:proc.exit}
          :reaction (fn [this data]
                      (when-not (:connected @this)
                        (notifos/done-working)
                        (popup/popup! {:header "We couldn't connect."
                                       :body [:span "There was an issue trying to connect to the project.
                                              Here's what we got:" [:pre (:buffer @this)]]
                                       :buttons [{:label "close"}]})
                        (clients/rem! (:client @this)))
                      (proc/kill-all (:procs @this))
                      (object/destroy! this)))

(object/object* ::connecting-notifier
                :triggers []
                :behaviors [::on-exit ::on-error ::on-out]
                :init (fn [this client]
                        (object/merge! this {:client client :buffer ""})
                        nil))

;; When is this triggered? ::eval! above passes try-connect along by itself.
(behavior ::connect
          :triggers #{:connect}
          :reaction (fn [this path]
                      (try-connect {:info {:path path}})))


;;****************************************************
;; Eval code
;;****************************************************

;; Showing results of evaluation Go code
(behavior ::go-result
          :triggers #{:editor.eval.go.result}
          :reaction (fn [editor res]
                      (notifos/done-working)
                      (object/raise editor
                                    :editor.result
                                    (:result res)
                                    {:line (:end (:meta res))
                                     :start-line (-> res :meta :start)})))

;; Behaviours related to initiating evaluation of go code.
(behavior ::on-eval.one
          :triggers #{:eval.one}
          :reaction (fn [editor]
                      (let [pos (ed/->cursor editor)
                            info (:info @editor)
                            info (if (ed/selection? editor)
                                   (assoc info
                                     :code (ed/selection editor)
                                     :meta {:start (-> (ed/->cursor editor "start") :line)
                                            :end (-> (ed/->cursor editor "end") :line)})
                                   (assoc info :pos pos :code code))]
                        (object/raise go :eval! {:origin editor
                                                 :info info}))))

(behavior ::eval!
          :triggers #{:eval!}
          :reaction (fn [this event]
                      (let [{:keys [info origin]} event]
                        (notifos/working "")
                        (clients/send (eval/get-client! {:command :editor.eval.go
                                                         :origin origin
                                                         :info info
                                                         :create try-connect})
                                      :editor.eval.go
                                      info
                                      :only
                                      origin)
                        (notifos/done-working))))

;;****************************************************
;; Utility functions
;;****************************************************

;; This is, *sadly*, required to run other processes via JS.
;; Sometimes the amount of abstractions to deal with to do
;; simple things is ridiculous.

(def exec (.-exec (js/require "child_process")))
(def user-env js/process.env)

(def plugin-dir
  "Gets the directory of this plugin, whether its in the user or light table plugins directory"
  (let [plugin-dir (files/join plugins/plugins-dir "LightTable-Go")
        user-plugin-dir (files/join plugins/user-plugins-dir "LightTable-Go")]
    (if (files/exists? plugin-dir) plugin-dir user-plugin-dir)))

(def client-path
  "Provides a fallback path at dev stage for plugins/*plugin-dir*, which
  returns an empty string when using LightTable UI."
    (files/join plugin-dir "client" "light_table_go_client.go"))

(defn cwf->path []
  "Returns current working file path."
  (-> (pool/last-active) tabs/->path))

(defn cwd->path []
  "Returns current working directory."
    (files/parent (cwf->path)))

(defn run-cmd
  ([cmd]
     (run-cmd cmd (fn [x y z] (identity x)) (fn [x y z] (println "Error: " x ))))
  ([cmd succ-cb]
     (run-cmd cmd succ-cb (fn [x y z] (println "Error: " x ))))
  ([cmd succ-cb fail-cb & args]
    "Runs `cmd` with `args` and executes callbacks when finished. Probably buggy, check implementation."
    (let [cwd (cwd->path)
          ;Options for node's exec function
          options (js-obj "cwd" cwd
                          "env" user-env)]
          (exec (str cmd args)
                     options
                    (fn [err stdout stderr]
                      (if err
                        (fail-cb err stdout stderr)
                        (succ-cb err stdout stderr)))))))

(defn console-callback
  ([] (console-callback nil))
  ([err?] (console-callback nil true))
  ([err? done?] (console-callback err? done? true))
  ([err? done? show?]
   (fn [res-or-err stdout stderr]
     (let [outfn console/log
           errfn console/error]
       (when (not err?) (outfn res-or-err))
       (when stdout (outfn stdout))
       (when stderr (errfn stderr)))
     (when done? (notifos/done-working))
     (when show? (cmd/exec! :console.show)))))

(defn make-cb [err? done?]
  (console-callback err?
                    done?
                    (get-setting :go-show-console)))

(defn succ-cb [] (make-cb false true))
(defn err-cb [] (make-cb true true))

(defn get-last-active-editor []
  "Gets the most recently accessed editor"
  (pool/last-active))

;;****************************************************
;; Build & run related functions
;;****************************************************

(defn gobuild [file]
  "Performs `go build file`."
  (let [cmd (str (get-setting :go-build-command) " " (str (files/parent file) files/separator "main.exe" ) " " file )]
    (notifos/working cmd)
    (run-cmd cmd (succ-cb) (err-cb))
    (notifos/done-working "")))

(defn gorun [file]
  "Performs `go run file`."
  (let [cmd (str (get-setting :go-run-command) " " file)]
    (notifos/working cmd)
    (run-cmd cmd (succ-cb) (err-cb))))

;;****************************************************
;; Formatting
;;****************************************************

(defn gofmt [file]
  "Performs `gofmt -w file`."
  (let [cmd (str (get-setting :go-fmt-command) " " file)
        editor (get-last-active-editor)
        pos (ed/->cursor editor)
        scroll (.getScrollInfo (ed/->cm-ed editor)) ;.getScrollInfo isn't aliased by Light Table yet, so we're calling it directly on Codemirror. This should change if it ever becomes available (pull request?)
        success-callback (fn []
          (pool/reload editor)
          (ed/move-cursor editor pos)
          (ed/scroll-to editor (.-left scroll) (.-top scroll))
          (notifos/done-working "Finished"))
        failure-callback (fn []
          (notifos/done-working "Unable to format"))]
    (notifos/working (str (get-setting :go-fmt-command) " " file "..."))
    (run-cmd cmd success-callback failure-callback)))

;;****************************************************
;; Testing
;;****************************************************

(defn gotest [file]
  "Performs `go test`."
  (let [cmd (str (get-setting :go-test-command))]
    (notifos/working cmd)
    (run-cmd cmd (succ-cb) (err-cb))))

;;****************************************************
;; Commands
;;****************************************************

(cmd/command {:command ::go-fmt
              :desc "Go: fmt current file"
              :exec (fn []
                      (let [editor (get-last-active-editor)]
                        (if (ed/dirty? editor)
                          (popup/popup! {:header "Save file?"
                             :body [:div
                                     [:p "You need to save this file before you format it."]]
                             :buttons [{:label "Cancel"}
                                       {:label "Save"
                                        :action (fn []
                                                  (object/raise editor :save)
                                                  (gofmt (cwf->path)))}]}) ;We don't need to explicitly call fmt since fmt-on-save is bound to :save
                          ;
                          )))})

(cmd/command {:command ::go-build
              :desc "Go: Build current file"
              :exec (fn []
                      (gobuild (cwf->path)))})

(cmd/command {:command ::go-run
              :desc "Go: Run current file"
              :exec (fn []
                      (gorun (cwf->path)))})

(cmd/command {:command ::go-test
              :desc "Go: Run tests in directory of current file"
              :exec (fn []
                      (gotest (cwd->path)))})

(cmd/command {:command ::go-plugin-help
              :desc "Go: Show plugin readme"
              :exec (fn []
                      (let [path (files/join plugin-dir "README.md")]
                          (cmd/exec! :open-path path)))})
                          ;(if-let [editor (first (pool/by-path path))] Doesn't seem to work?
                           ; (object/add-tags editor :editor.read-only))))})


;;****************************************************
;; Autocomplete
;;****************************************************

(behavior ::trigger-update-hints
          :triggers #{:editor.go.hints.update!}
          :debounce 100
          :reaction (fn [editor res]
                      (let [command :editor.go.hints
                            cursor-location (ed/->cursor editor)
                            info (assoc (@editor :info)
                                  :pos cursor-location
                                  :line (ed/line editor (:line cursor-location))
                                  :code (.getValue (ed/get-doc editor))
                                  :path (cwf->path))]
                          (clients/send (eval/get-client! {:command command
                                                           :info info
                                                           :origin editor
                                                           :create try-connect})
                                        command
                                        info
                                        :only
                                        editor)
                          )))

;Callback when suggestions are returned from the client
(behavior ::finish-update-hints
          :triggers #{:editor.go.hints.result}
          :reaction (fn [editor res]
                      ;Autocomplete suggestions are of the format (#js {:completion "suggestion1"} #js {:completion "suggestion2"})
                      (let [hints (map #(do #js {:completion %})(:hints res))]
                        (object/merge! editor {::hints hints})
                        (object/raise auto-complete/hinter :refresh!))))

;Autocomplete starts here.
(behavior ::use-local-hints
          :triggers #{:hints+}
          :reaction (fn [editor hints token]
                      (when (not= token (::token @editor)) ;Prevents autocomplete from being triggered when nothing's changed
                        (object/merge! editor {::token token})
                        (object/raise editor :editor.go.hints.update!))
                      (if-let [go-hints (::hints @editor)] ;Makes sure that the hint box opens even if we don't have any hints from the client
                        go-hints
                        (:lt.plugins.auto-complete/hints @editor))))

;;****************************************************
;; Docs
;;****************************************************

(behavior ::go-inline-doc
          :triggers #{:editor.doc}
          :reaction (fn [editor]
                      (let [command :editor.go.doc
                          cursor-location (ed/->cursor editor)
                          info (assoc (@editor :info)
                                 :pos cursor-location
                                 :line (ed/line editor (:line cursor-location))
                                 :code (.getValue (ed/get-doc editor))
                                 :path (cwf->path))]
                          (clients/send (eval/get-client! {:command command
                                                           :info info
                                                           :origin editor
                                                           :create try-connect})
                                        command
                                        info
                                        :only
                                        editor)
                          )))

; :name :ns :args :doc
(behavior ::print-inline-doc
          :triggers #{:editor.go.doc} ;Todo - Modify the client so that it just echos the command back. We don't need two triggers for each command
          :reaction (fn [editor result]
                      (object/raise editor :editor.doc.show! (:doc result))))
