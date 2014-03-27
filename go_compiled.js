if(!lt.util.load.provided_QMARK_('lt.plugins.go')) {
goog.provide('lt.plugins.go');
goog.require('cljs.core');
goog.require('lt.objs.plugins');
goog.require('lt.objs.files');
goog.require('lt.objs.tabs');
goog.require('lt.objs.popup');
goog.require('lt.objs.popup');
goog.require('lt.plugins.auto_complete');
goog.require('lt.objs.notifos');
goog.require('lt.objs.proc');
goog.require('lt.objs.notifos');
goog.require('lt.objs.editor.pool');
goog.require('lt.objs.command');
goog.require('lt.objs.files');
goog.require('lt.objs.clients.tcp');
goog.require('lt.objs.plugins');
goog.require('lt.plugins.auto_complete');
goog.require('lt.objs.eval');
goog.require('lt.objs.clients');
goog.require('lt.objs.clients.tcp');
goog.require('lt.objs.editor');
goog.require('lt.object');
goog.require('lt.object');
goog.require('lt.objs.console');
goog.require('lt.objs.proc');
goog.require('lt.objs.tabs');
goog.require('lt.objs.console');
goog.require('lt.objs.eval');
goog.require('lt.objs.clients');
goog.require('lt.objs.editor.pool');
goog.require('lt.objs.command');
goog.require('lt.objs.editor');
lt.object.object_STAR_.call(null,new cljs.core.Keyword("lt.plugins.go","go-lang","lt.plugins.go/go-lang",1766929064),new cljs.core.Keyword(null,"tags","tags",1017456523),new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"go.lang","go.lang",1161134758),null], null), null),new cljs.core.Keyword(null,"behaviors","behaviors",607554515),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword("lt.plugins.go","fmt-on-save","lt.plugins.go/fmt-on-save",1478325872)], null));
lt.plugins.go.exec = require("child_process").exec;
/**
* Provides a fallback path at dev stage for plugins/*plugin-dir*, which
* returns an empty string when using LightTable UI.
*/
lt.plugins.go.client_path = (function (){var dir = lt.objs.plugins._STAR_plugin_dir_STAR_;return lt.objs.files.join.call(null,(((dir == null))?lt.objs.plugins.plugins_dir:dir),"go","client","echo_server.go");
})();
lt.plugins.go.go = lt.object.create.call(null,new cljs.core.Keyword("lt.plugins.go","go-lang","lt.plugins.go/go-lang",1766929064));
lt.plugins.go.try_connect = (function try_connect(p__8567){var map__8569 = p__8567;var map__8569__$1 = ((cljs.core.seq_QMARK_.call(null,map__8569))?cljs.core.apply.call(null,cljs.core.hash_map,map__8569):map__8569);var info = cljs.core.get.call(null,map__8569__$1,new cljs.core.Keyword(null,"info","info",1017141280));var path = new cljs.core.Keyword(null,"path","path",1017337751).cljs$core$IFn$_invoke$arity$1(info);var client = lt.objs.clients.client_BANG_.call(null,new cljs.core.Keyword(null,"go.client","go.client",520858371));var obj = lt.object.create.call(null,new cljs.core.Keyword("lt.plugins.go","connecting-notifier","lt.plugins.go/connecting-notifier",2052045824),client);lt.object.add_tags.call(null,client,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"tcp.client","tcp.client",3877789162)], null));
lt.objs.proc.exec.call(null,new cljs.core.PersistentArrayMap(null, 5, [new cljs.core.Keyword(null,"command","command",1964298941),"go",new cljs.core.Keyword(null,"args","args",1016906831),new cljs.core.PersistentVector(null, 4, 5, cljs.core.PersistentVector.EMPTY_NODE, ["run",lt.plugins.go.client_path,lt.objs.clients.tcp.port,lt.objs.clients.__GT_id.call(null,client)], null),new cljs.core.Keyword(null,"cwd","cwd",1014003170),".",new cljs.core.Keyword(null,"env","env",1014004831),cljs.core.PersistentArrayMap.EMPTY,new cljs.core.Keyword(null,"obj","obj",1014014057),obj], null));
return client;
});
lt.plugins.go.__BEH__on_out = (function __BEH__on_out(this$,data){lt.objs.console.log.call(null,":on-out");
var out = data.toString();lt.object.update_BANG_.call(null,this$,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"buffer","buffer",3930752946)], null),cljs.core.str,out);
if((out.indexOf("Connected") > -1))
{lt.objs.notifos.done_working.call(null);
return lt.object.merge_BANG_.call(null,this$,new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"connected","connected",4729661051),true], null));
} else
{return null;
}
});
lt.object.behavior_STAR_.call(null,new cljs.core.Keyword("lt.plugins.go","on-out","lt.plugins.go/on-out",2861001077),new cljs.core.Keyword(null,"reaction","reaction",4441361819),lt.plugins.go.__BEH__on_out,new cljs.core.Keyword(null,"triggers","triggers",2516997421),new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"proc.out","proc.out",4302083112),null], null), null));
lt.plugins.go.__BEH__on_error = (function __BEH__on_error(this$,data){lt.objs.console.log.call(null,":on-error");
var out = data.toString();lt.objs.console.log.call(null,out);
if((new cljs.core.Keyword(null,"buffer","buffer",3930752946).cljs$core$IFn$_invoke$arity$1(cljs.core.deref.call(null,this$)).indexOf("Connected") > -1))
{return null;
} else
{return lt.object.update_BANG_.call(null,this$,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"buffer","buffer",3930752946)], null),cljs.core.str,data);
}
});
lt.object.behavior_STAR_.call(null,new cljs.core.Keyword("lt.plugins.go","on-error","lt.plugins.go/on-error",2050341759),new cljs.core.Keyword(null,"reaction","reaction",4441361819),lt.plugins.go.__BEH__on_error,new cljs.core.Keyword(null,"triggers","triggers",2516997421),new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"proc.error","proc.error",4143512802),null], null), null));
lt.plugins.go.__BEH__on_exit = (function __BEH__on_exit(this$,data){lt.objs.console.log.call(null,":on-exit");
if(cljs.core.truth_(new cljs.core.Keyword(null,"connected","connected",4729661051).cljs$core$IFn$_invoke$arity$1(cljs.core.deref.call(null,this$))))
{} else
{lt.objs.notifos.done_working.call(null);
lt.objs.popup.popup_BANG_.call(null,new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"header","header",4087600639),"We couldn't connect.",new cljs.core.Keyword(null,"body","body",1016933652),new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"span","span",1017440956),"There was an issue trying to connect to the project.\n                                              Here's what we got:",new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"pre","pre",1014015509),new cljs.core.Keyword(null,"buffer","buffer",3930752946).cljs$core$IFn$_invoke$arity$1(cljs.core.deref.call(null,this$))], null)], null),new cljs.core.Keyword(null,"buttons","buttons",1255256819),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"label","label",1116631654),"close"], null)], null)], null));
lt.objs.clients.rem_BANG_.call(null,new cljs.core.Keyword(null,"client","client",3951159101).cljs$core$IFn$_invoke$arity$1(cljs.core.deref.call(null,this$)));
}
lt.objs.proc.kill_all.call(null,new cljs.core.Keyword(null,"procs","procs",1120844623).cljs$core$IFn$_invoke$arity$1(cljs.core.deref.call(null,this$)));
return lt.object.destroy_BANG_.call(null,this$);
});
lt.object.behavior_STAR_.call(null,new cljs.core.Keyword("lt.plugins.go","on-exit","lt.plugins.go/on-exit",4565175113),new cljs.core.Keyword(null,"reaction","reaction",4441361819),lt.plugins.go.__BEH__on_exit,new cljs.core.Keyword(null,"triggers","triggers",2516997421),new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"proc.exit","proc.exit",4162906152),null], null), null));
lt.object.object_STAR_.call(null,new cljs.core.Keyword("lt.plugins.go","connecting-notifier","lt.plugins.go/connecting-notifier",2052045824),new cljs.core.Keyword(null,"triggers","triggers",2516997421),cljs.core.PersistentVector.EMPTY,new cljs.core.Keyword(null,"behaviors","behaviors",607554515),new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword("lt.plugins.go","on-exit","lt.plugins.go/on-exit",4565175113),new cljs.core.Keyword("lt.plugins.go","on-error","lt.plugins.go/on-error",2050341759),new cljs.core.Keyword("lt.plugins.go","on-out","lt.plugins.go/on-out",2861001077)], null),new cljs.core.Keyword(null,"init","init",1017141378),(function (this$,client){lt.object.merge_BANG_.call(null,this$,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"client","client",3951159101),client,new cljs.core.Keyword(null,"buffer","buffer",3930752946),""], null));
return null;
}));
lt.plugins.go.__BEH__on_eval__DOT__one = (function __BEH__on_eval__DOT__one(editor){var pos = lt.objs.editor.__GT_cursor.call(null,editor);var info = new cljs.core.Keyword(null,"info","info",1017141280).cljs$core$IFn$_invoke$arity$1(cljs.core.deref.call(null,editor));var info__$1 = (cljs.core.truth_(lt.objs.editor.selection_QMARK_.call(null,editor))?cljs.core.assoc.call(null,info,new cljs.core.Keyword(null,"code","code",1016963423),lt.objs.editor.selection.call(null,editor),new cljs.core.Keyword(null,"meta","meta",1017252215),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"start","start",1123661780),new cljs.core.Keyword(null,"line","line",1017226086).cljs$core$IFn$_invoke$arity$1(lt.objs.editor.__GT_cursor.call(null,editor,"start")),new cljs.core.Keyword(null,"end","end",1014004813),new cljs.core.Keyword(null,"line","line",1017226086).cljs$core$IFn$_invoke$arity$1(lt.objs.editor.__GT_cursor.call(null,editor,"end"))], null)):cljs.core.assoc.call(null,info,new cljs.core.Keyword(null,"pos","pos",1014015430),pos,new cljs.core.Keyword(null,"code","code",1016963423),lt.plugins.go.code));return lt.object.raise.call(null,lt.plugins.go.go,new cljs.core.Keyword(null,"eval!","eval!",1110791799),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"origin","origin",4300251800),editor,new cljs.core.Keyword(null,"info","info",1017141280),info__$1], null));
});
lt.object.behavior_STAR_.call(null,new cljs.core.Keyword("lt.plugins.go","on-eval.one","lt.plugins.go/on-eval.one",4325210455),new cljs.core.Keyword(null,"reaction","reaction",4441361819),lt.plugins.go.__BEH__on_eval__DOT__one,new cljs.core.Keyword(null,"triggers","triggers",2516997421),new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"eval.one","eval.one",1173589382),null], null), null));
lt.plugins.go.__BEH__eval_BANG_ = (function __BEH__eval_BANG_(this$,event){var map__8571 = event;var map__8571__$1 = ((cljs.core.seq_QMARK_.call(null,map__8571))?cljs.core.apply.call(null,cljs.core.hash_map,map__8571):map__8571);var origin = cljs.core.get.call(null,map__8571__$1,new cljs.core.Keyword(null,"origin","origin",4300251800));var info = cljs.core.get.call(null,map__8571__$1,new cljs.core.Keyword(null,"info","info",1017141280));lt.objs.notifos.working.call(null,"");
lt.objs.clients.send.call(null,lt.objs.eval.get_client_BANG_.call(null,new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"command","command",1964298941),new cljs.core.Keyword(null,"editor.eval.go","editor.eval.go",1847417707),new cljs.core.Keyword(null,"origin","origin",4300251800),origin,new cljs.core.Keyword(null,"info","info",1017141280),info,new cljs.core.Keyword(null,"create","create",3956577390),lt.plugins.go.try_connect], null)),new cljs.core.Keyword(null,"editor.eval.go","editor.eval.go",1847417707),info,new cljs.core.Keyword(null,"only","only",1017320222),origin);
return lt.objs.notifos.done_working.call(null);
});
lt.object.behavior_STAR_.call(null,new cljs.core.Keyword("lt.plugins.go","eval!","lt.plugins.go/eval!",1817136658),new cljs.core.Keyword(null,"reaction","reaction",4441361819),lt.plugins.go.__BEH__eval_BANG_,new cljs.core.Keyword(null,"triggers","triggers",2516997421),new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"eval!","eval!",1110791799),null], null), null));
lt.plugins.go.__BEH__connect = (function __BEH__connect(this$,path){return lt.plugins.go.try_connect.call(null,new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"info","info",1017141280),new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"path","path",1017337751),path], null)], null));
});
lt.object.behavior_STAR_.call(null,new cljs.core.Keyword("lt.plugins.go","connect","lt.plugins.go/connect",2577217519),new cljs.core.Keyword(null,"reaction","reaction",4441361819),lt.plugins.go.__BEH__connect,new cljs.core.Keyword(null,"triggers","triggers",2516997421),new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"connect","connect",1965255772),null], null), null));
lt.plugins.go.__BEH__go_result = (function __BEH__go_result(editor,res){lt.objs.notifos.done_working.call(null);
return lt.object.raise.call(null,editor,new cljs.core.Keyword(null,"editor.result","editor.result",4030217008),new cljs.core.Keyword(null,"result","result",4374444943).cljs$core$IFn$_invoke$arity$1(res),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"line","line",1017226086),new cljs.core.Keyword(null,"end","end",1014004813).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"meta","meta",1017252215).cljs$core$IFn$_invoke$arity$1(res)),new cljs.core.Keyword(null,"start-line","start-line",3689311729),new cljs.core.Keyword(null,"start","start",1123661780).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"meta","meta",1017252215).cljs$core$IFn$_invoke$arity$1(res))], null));
});
lt.object.behavior_STAR_.call(null,new cljs.core.Keyword("lt.plugins.go","go-result","lt.plugins.go/go-result",2807577527),new cljs.core.Keyword(null,"reaction","reaction",4441361819),lt.plugins.go.__BEH__go_result,new cljs.core.Keyword(null,"triggers","triggers",2516997421),new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"editor.eval.go.result","editor.eval.go.result",1472184100),null], null), null));
lt.plugins.go.cwf__GT_path = (function cwf__GT_path(){return lt.objs.tabs.__GT_path.call(null,lt.objs.editor.pool.last_active.call(null));
});
/**
* @param {...*} var_args
*/
lt.plugins.go.run_cmd = (function() { 
var run_cmd__delegate = function (cmd,args){var child = lt.plugins.go.exec.call(null,[cljs.core.str(cmd),cljs.core.str(args)].join(''),null,(function (err,stdout,stderr){if(cljs.core.truth_(err))
{return cljs.core.println.call(null,"err: ",err);
} else
{return cljs.core.println.call(null,stdout);
}
}));return null;
};
var run_cmd = function (cmd,var_args){
var args = null;if (arguments.length > 1) {
  args = cljs.core.array_seq(Array.prototype.slice.call(arguments, 1),0);} 
return run_cmd__delegate.call(this,cmd,args);};
run_cmd.cljs$lang$maxFixedArity = 1;
run_cmd.cljs$lang$applyTo = (function (arglist__8573){
var cmd = cljs.core.first(arglist__8573);
var args = cljs.core.rest(arglist__8573);
return run_cmd__delegate(cmd,args);
});
run_cmd.cljs$core$IFn$_invoke$arity$variadic = run_cmd__delegate;
return run_cmd;
})()
;
lt.plugins.go.gofmt = (function gofmt(file){var cmd = [cljs.core.str("gofmt -w "),cljs.core.str(file)].join('');lt.objs.notifos.working.call(null,[cljs.core.str("gofmt "),cljs.core.str(file),cljs.core.str("...")].join(''));
lt.plugins.go.run_cmd.call(null,cmd);
return lt.objs.notifos.done_working.call(null,"");
});
lt.plugins.go.__BEH__fmt_on_save = (function __BEH__fmt_on_save(editor){var path = new cljs.core.Keyword(null,"path","path",1017337751).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"info","info",1017141280).cljs$core$IFn$_invoke$arity$1(cljs.core.deref.call(null,editor)));return lt.plugins.go.gofmt.call(null,path);
});
lt.object.behavior_STAR_.call(null,new cljs.core.Keyword("lt.plugins.go","fmt-on-save","lt.plugins.go/fmt-on-save",1478325872),new cljs.core.Keyword(null,"reaction","reaction",4441361819),lt.plugins.go.__BEH__fmt_on_save,new cljs.core.Keyword(null,"triggers","triggers",2516997421),new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"save","save",1017427183),null], null), null));
lt.objs.command.command.call(null,new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"command","command",1964298941),new cljs.core.Keyword("lt.plugins.go","go-fmt","lt.plugins.go/go-fmt",4770781485),new cljs.core.Keyword(null,"desc","desc",1016984067),"Go: fmt current file",new cljs.core.Keyword(null,"exec","exec",1017031683),(function (){return lt.plugins.go.gofmt.call(null,lt.plugins.go.cwf__GT_path.call(null));
})], null));
lt.plugins.go.gobuild = (function gobuild(file){var cmd = [cljs.core.str("go build -o "),cljs.core.str([cljs.core.str(lt.objs.files.parent.call(null,file)),cljs.core.str(lt.objs.files.separator),cljs.core.str("main.exe")].join('')),cljs.core.str(" "),cljs.core.str(file)].join('');lt.objs.notifos.working.call(null,[cljs.core.str("go build "),cljs.core.str(file)].join(''));
lt.plugins.go.run_cmd.call(null,cmd);
return lt.objs.notifos.done_working.call(null,"");
});
lt.objs.command.command.call(null,new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"command","command",1964298941),new cljs.core.Keyword("lt.plugins.go","go-build","lt.plugins.go/go-build",1950029902),new cljs.core.Keyword(null,"desc","desc",1016984067),"Go: Build current file",new cljs.core.Keyword(null,"exec","exec",1017031683),(function (){return lt.plugins.go.gobuild.call(null,lt.plugins.go.cwf__GT_path.call(null));
})], null));
lt.plugins.go.gorun = (function gorun(file){var cmd = [cljs.core.str("go run "),cljs.core.str(file)].join('');lt.objs.notifos.working.call(null,[cljs.core.str("go run "),cljs.core.str(file)].join(''));
lt.plugins.go.run_cmd.call(null,cmd);
return lt.objs.notifos.done_working.call(null);
});
lt.objs.command.command.call(null,new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"command","command",1964298941),new cljs.core.Keyword("lt.plugins.go","go-run","lt.plugins.go/go-run",4770759475),new cljs.core.Keyword(null,"desc","desc",1016984067),"Go: Run current file",new cljs.core.Keyword(null,"exec","exec",1017031683),(function (){return lt.plugins.go.gorun.call(null,lt.plugins.go.cwf__GT_path.call(null));
})], null));
lt.plugins.go.__BEH__trigger_update_hints = (function __BEH__trigger_update_hints(editor,res){var command = new cljs.core.Keyword(null,"editor.go.hints","editor.go.hints",1189063961);var cursor_location = lt.objs.editor.__GT_cursor.call(null,editor);var info = cljs.core.assoc.call(null,cljs.core.deref.call(null,editor).call(null,new cljs.core.Keyword(null,"info","info",1017141280)),new cljs.core.Keyword(null,"pos","pos",1014015430),cursor_location,new cljs.core.Keyword(null,"line","line",1017226086),lt.objs.editor.line.call(null,editor,new cljs.core.Keyword(null,"line","line",1017226086).cljs$core$IFn$_invoke$arity$1(cursor_location)),new cljs.core.Keyword(null,"code","code",1016963423),lt.objs.editor.get_doc.call(null,editor).getValue(),new cljs.core.Keyword(null,"path","path",1017337751),lt.plugins.go.cwf__GT_path.call(null));return lt.objs.clients.send.call(null,lt.objs.eval.get_client_BANG_.call(null,new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"command","command",1964298941),command,new cljs.core.Keyword(null,"info","info",1017141280),info,new cljs.core.Keyword(null,"origin","origin",4300251800),editor,new cljs.core.Keyword(null,"create","create",3956577390),lt.plugins.go.try_connect], null)),command,info,new cljs.core.Keyword(null,"only","only",1017320222),editor);
});
lt.object.behavior_STAR_.call(null,new cljs.core.Keyword("lt.plugins.go","trigger-update-hints","lt.plugins.go/trigger-update-hints",3312848282),new cljs.core.Keyword(null,"reaction","reaction",4441361819),lt.plugins.go.__BEH__trigger_update_hints,new cljs.core.Keyword(null,"triggers","triggers",2516997421),new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"editor.go.hints.update!","editor.go.hints.update!",2688026275),null], null), null),new cljs.core.Keyword(null,"debounce","debounce",1556599227),100);
lt.plugins.go.__BEH__finish_update_hints = (function __BEH__finish_update_hints(editor,res){var hints = cljs.core.map.call(null,(function (p1__8572_SHARP_){return {"completion": p1__8572_SHARP_};
}),new cljs.core.Keyword(null,"hints","hints",1113187902).cljs$core$IFn$_invoke$arity$1(res));lt.object.merge_BANG_.call(null,editor,new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword("lt.plugins.go","hints","lt.plugins.go/hints",1818336713),hints], null));
return lt.object.raise.call(null,lt.plugins.auto_complete.hinter,new cljs.core.Keyword(null,"refresh!","refresh!",4597922840));
});
lt.object.behavior_STAR_.call(null,new cljs.core.Keyword("lt.plugins.go","finish-update-hints","lt.plugins.go/finish-update-hints",1000672983),new cljs.core.Keyword(null,"reaction","reaction",4441361819),lt.plugins.go.__BEH__finish_update_hints,new cljs.core.Keyword(null,"triggers","triggers",2516997421),new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"editor.go.hints.result","editor.go.hints.result",1526517174),null], null), null));
lt.plugins.go.__BEH__use_local_hints = (function __BEH__use_local_hints(editor,hints,token){if(cljs.core.not_EQ_.call(null,token,new cljs.core.Keyword("lt.plugins.go","token","lt.plugins.go/token",1736308958).cljs$core$IFn$_invoke$arity$1(cljs.core.deref.call(null,editor))))
{lt.object.merge_BANG_.call(null,editor,new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword("lt.plugins.go","token","lt.plugins.go/token",1736308958),token], null));
lt.object.raise.call(null,editor,new cljs.core.Keyword(null,"editor.go.hints.update!","editor.go.hints.update!",2688026275));
} else
{}
var temp__4090__auto__ = new cljs.core.Keyword("lt.plugins.go","hints","lt.plugins.go/hints",1818336713).cljs$core$IFn$_invoke$arity$1(cljs.core.deref.call(null,editor));if(cljs.core.truth_(temp__4090__auto__))
{var go_hints = temp__4090__auto__;return go_hints;
} else
{return new cljs.core.Keyword("lt.plugins.auto-complete","hints","lt.plugins.auto-complete/hints",3881612567).cljs$core$IFn$_invoke$arity$1(cljs.core.deref.call(null,editor));
}
});
lt.object.behavior_STAR_.call(null,new cljs.core.Keyword("lt.plugins.go","use-local-hints","lt.plugins.go/use-local-hints",1867406769),new cljs.core.Keyword(null,"reaction","reaction",4441361819),lt.plugins.go.__BEH__use_local_hints,new cljs.core.Keyword(null,"triggers","triggers",2516997421),new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"hints+","hints+",4091697745),null], null), null));
}

//# sourceMappingURL=go_compiled.js.map