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
                :behaviors [])

;; Create object ::go-lang
(def go (object/create ::go-lang))

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
                      (console/log ":on-out")
                      (let [out (.toString data)]
                        (object/update! this [:buffer] str out)
                        (when (> (.indexOf out "connected") -1)
                          (do
                            (notifos/done-working)
                            (object/merge! this {:connected true})
                            )))))

(behavior ::on-error
          :triggers #{:proc.error}
          :reaction (fn [this data]
                      (console/log ":on-error")
                      (let [out (.toString data)]
                        (console/log out)
                        (when-not (> (.indexOf (:buffer @this) "connected") -1)
                          (object/update! this [:buffer] str data)))))

(behavior ::on-exit
          :triggers #{:proc.exit}
          :reaction (fn [this data]
                      (console/log ":on-exit")
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

(def client-path
  "Provides a fallback path at dev stage for plugins/*plugin-dir*, which
  returns an empty string when using LightTable UI."
  (let [dir plugins/*plugin-dir*]
    (files/join (if (nil? dir) plugins/plugins-dir dir)
                "LightTable-Go" "client" "light_table_go_client.go")))

(defn cwf->path []
  "Returns current working file path."
  (-> (pool/last-active) tabs/->path))

(defn run-cmd
  ([cmd]
     (run-cmd cmd identity identity))
  ([cmd succ-cb]
     (run-cmd cmd succ-cb identity))
  ([cmd succ-cb fail-cb & args]
    "Runs `cmd` with `args` and executes callbacks when finished. Probably buggy, check implementation."
    (let [child (exec (str cmd args)
                           nil
                           (fn [err stdout stderr]
                             (if err
                               (do (println "err: " err) (fail-cb))
                               (succ-cb stdout))))])))

(defn get-last-active-editor []
  "Gets the most recently accessed editor"
  (pool/last-active))

;;****************************************************
;; Build & run related functions
;;****************************************************

(defn gobuild [file]
  "Performs `go build file`."
  (let [cmd (str "go build -o " (str (files/parent file) files/separator "main.exe" ) " " file )]
    (notifos/working (str "go build " file))
    (run-cmd cmd)
    (notifos/done-working "")))

(defn gorun [file]
  "Performs `go run file`."
  (let [cmd (str "go run " file )]
    (notifos/working (str "go run " file ))
    (run-cmd cmd)
    (notifos/done-working)))

;;****************************************************
;; Formatting
;;****************************************************

(defn gofmt [file]
  "Performs `gofmt -w file`."
  (let [cmd (str "gofmt -w=true " file)
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
    (notifos/working (str "gofmt " file "..."))
    (run-cmd cmd success-callback failure-callback)))

(behavior ::fmt-on-save
          :triggers #{:save}
          :reaction (fn [editor]
                      (let [path (-> @editor :info :path)]
                        (gofmt path))))

;;****************************************************
;; Commands
;;****************************************************

(cmd/command {:command ::go-fmt
              :desc "Go: fmt current file"
              :exec (fn []
                      ;TODO Ask user if they want to save
                      (let [editor (get-last-active-editor)]
                        (if (ed/dirty? editor)
                          (popup/popup! {:header "Save file?"
                             :body [:div
                                     [:p "You need to save this file before you format it."]]
                             :buttons [{:label "Cancel"}
                                       {:label "Save"
                                        :action (fn []
                                                  (object/raise editor :save))}]}) ;We don't need to explicitly call fmt since fmt-on-save is bound to :save
                          (gofmt (cwf->path)))))})

(cmd/command {:command ::go-build
              :desc "Go: Build current file"
              :exec (fn []
                      (gobuild (cwf->path)))})

(cmd/command {:command ::go-run
              :desc "Go: Run current file"
              :exec (fn []
                      (gorun (cwf->path)))})

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
