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

;; Declares a new object called "go-lang" and also lets you assign things to it;
;; in your case a set of with just a tag.
(object/object* ::go-lang
                :tags #{:go.lang}
                :behaviors [::fmt-on-save])

;; This is, *sadly*, required to run other processes via JS.
;; Sometimes the amount of abstractions to deal with to do
;; simple things is ridiculous.
(def exec (.-exec (js/require "child_process")))


(def client-path
  "Provides a fallback path at dev stage for plugins/*plugin-dir*, which
  returns an empty string when using LightTable UI."
  (let [dir plugins/*plugin-dir*]
    (files/join (if (nil? dir) plugins/plugins-dir dir)
                "go" "client" "echo_server.go")))

;; Create object ::go-lang
(def go (object/create ::go-lang))

;; Attempts to establish a connection with the Go client.
(defn try-connect [{:keys [info]}]
  (let [path (:path info)
        client (clients/client! :go.client)
        obj (object/create ::connecting-notifier client)]
    (object/add-tags client [:tcp.client])
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
                        (when (> (.indexOf out "Connected") -1)
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
                        (when-not (> (.indexOf (:buffer @this) "Connected") -1)
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

;; When is this triggered? ::eval! above passes try-connect along by itself.
(behavior ::connect
          :triggers #{:connect}
          :reaction (fn [this path]
                      (try-connect {:info {:path path}})))

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

;; go fmt
(defn cwf->path []
  "Returns current working file path."
  (-> (pool/last-active) tabs/->path))

(defn run-cmd [cmd & args]
  "Runs `cmd` with `args`. Probably buggy, check implementation."
  (let [child (exec (str cmd args)
                         nil
                         (fn [err stdout stderr]
                           (if err
                             (println "err: " err)
                             (println stdout))))
        ;input (.-stdin child)
        ;output (.-stdout child)
        ]
    ;(do (.end input))
    ))

(defn gofmt [file]
  "Performs `gofmt -w file`."
  (let [cmd (str "gofmt -w " file)]
    (notifos/working (str "gofmt " file "..."))
    (run-cmd cmd)
    (notifos/done-working "")))

;; This should trigger
(behavior ::fmt-on-save
          :triggers #{:save}
          :reaction (fn [editor]
                      (let [path (-> @editor :info :path)]
                        (gofmt path))))

(cmd/command {:command ::go-fmt
              :desc "Go: fmt current file"
              :exec (fn []
                      (gofmt (cwf->path)))})

;; go build
(defn gobuild [file]
  "Performs `go build file`."
  (let [cmd (str "go build -o " (str (files/parent file) files/separator "main.exe" ) " " file )]
    (notifos/working (str "go build " file))
    (run-cmd cmd)
    (notifos/done-working "")))

(cmd/command {:command ::go-build
              :desc "Go: Build current file"
              :exec (fn []
                      (gobuild (cwf->path)))})

;; go run
(defn gorun [file]
  "Performs `go run file`."
  (let [cmd (str "go run " file )]
    (notifos/working (str "go run " file ))
    (run-cmd cmd)
    (notifos/done-working)))

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


(behavior ::finish-update-hints
          :triggers #{:editor.go.hints.result}
          :reaction (fn [editor res]
                      ;Completions are of the format (#js {:completion "suggestion1"} #js {:completion "suggestion2"})
                      (let [hints (map #(do #js {:completion %})(:hints res))]
                        (object/merge! editor {::hints hints})
                        (object/raise auto-complete/hinter :refresh!))))



(behavior ::use-local-hints
          :triggers #{:hints+}
          :reaction (fn [editor hints token]
                      (when (not= token (::token @editor))
                        (object/merge! editor {::token token})
                        (object/raise editor :editor.go.hints.update!))
                      (if-let [go-hints (::hints @editor)]
                        go-hints
                        (:lt.plugins.auto-complete/hints @editor))))
