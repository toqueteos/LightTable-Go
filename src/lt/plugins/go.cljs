(ns lt.plugins.go
  (:require [lt.object :as object]
            [lt.objs.editor :as ed]
            [lt.objs.editor.pool :as pool]
            [lt.objs.command :as cmd]
            [lt.objs.clients.tcp :as tcp]
            [lt.objs.clients :as clients]
            [lt.objs.proc :as proc]
            [lt.objs.files :as files]
            [lt.objs.plugins :as plugins]
            [lt.objs.notifos :as notifos]
            [lt.objs.popup :as popup]
            [lt.objs.eval :as eval]
            [lt.objs.console :as console]

            [lt.objs.tabs :as tabs])
  (:require-macros [lt.macros :refer [behavior]]))

;; Declares a new object called "go-lang" and also lets you asign things to it;
;; in your case a set of with just a tag.
(object/object* ::go-lang
                :tags #{:go.lang})

;; Provide a fallback for plugins/*plugin-dir* which is an empty string
;; at dev stage (using LightTable UI).
(def client-path
  (let [dir plugins/*plugin-dir*]
    (if (nil? dir)
      (files/join plugins/plugins-dir "go" "client" "echo_server.go")
      (files/join dir))))

;; Create object ::go-lang
(def go (object/create ::go-lang))

;;
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
                      (let [{:keys [info origin]} event
                            client (-> @origin :client :default)]
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
                      (object/raise editor :editor.result (:result res) {:line (:end (:meta res))
                                                                         :start-line (-> res :meta :start)})))
