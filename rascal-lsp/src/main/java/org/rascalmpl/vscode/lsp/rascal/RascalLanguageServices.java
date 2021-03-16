/**
 * Copyright (c) 2018, Davy Landman, SWAT.engineering BV, 2020 Jurgen J. Vinju, NWO-I CWI All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.rascalmpl.vscode.lsp.rascal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.rascalmpl.debug.IRascalMonitor;
import org.rascalmpl.exceptions.Throw;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.control_exceptions.InterruptException;
import org.rascalmpl.library.lang.rascal.syntax.RascalParser;
import org.rascalmpl.library.util.PathConfig;
import org.rascalmpl.parser.Parser;
import org.rascalmpl.parser.gtd.io.InputConverter;
import org.rascalmpl.parser.gtd.result.action.IActionExecutor;
import org.rascalmpl.parser.gtd.result.out.DefaultNodeFlattener;
import org.rascalmpl.parser.uptr.UPTRNodeFactory;
import org.rascalmpl.parser.uptr.action.NoActionExecutor;
import org.rascalmpl.shell.ShellEvaluatorFactory;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.values.IRascalValueFactory;
import org.rascalmpl.values.parsetrees.ITree;
import org.rascalmpl.values.parsetrees.TreeAdapter;
import org.rascalmpl.vscode.lsp.util.LoggingMonitor;
import org.rascalmpl.vscode.lsp.util.concurrent.InterruptibleFuture;
import io.usethesource.vallang.IConstructor;
import io.usethesource.vallang.IList;
import io.usethesource.vallang.INode;
import io.usethesource.vallang.ISet;
import io.usethesource.vallang.ISourceLocation;
import io.usethesource.vallang.IString;
import io.usethesource.vallang.IValue;
import io.usethesource.vallang.IValueFactory;

public class RascalLanguageServices {
    private static final IValueFactory VF = IRascalValueFactory.getInstance();

    private static final Logger logger = LogManager.getLogger(RascalLanguageServices.class);

    private final Future<Evaluator> outlineEvaluator =
        makeFutureEvaluator("Rascal outline", "lang::rascal::ide::Outline");
    private final Future<Evaluator> summaryEvaluator =
        makeFutureEvaluator("Rascal summary", "lang::rascalcore::check::Summary");
    private final Future<Evaluator> compilerEvaluator =
        makeFutureEvaluator("Rascal compiler", "lang::rascalcore::check::Checker");

    public InterruptibleFuture<@Nullable IConstructor> getSummary(ISourceLocation occ, PathConfig pcfg, Executor exec) {
        try {
            IString moduleName = VF.string(pcfg.getModuleName(occ));
            return runEvaluator("makeSummary", summaryEvaluator, eval -> {
                IConstructor result = (IConstructor) eval.call("makeSummary", moduleName, pcfg.asConstructor());
                return result != null && result.asWithKeywordParameters().hasParameters() ? result : null;
            }, null, exec);
        } catch (IOException e) {
            logger.error("Error looking up module name from source location {}", occ, e);
            return new InterruptibleFuture<>(CompletableFuture.completedFuture(null), () -> {});
        }
    }

    public InterruptibleFuture<Map<ISourceLocation,ISet>> compileFolder(ISourceLocation folder, PathConfig pcfg, Executor exec) {
        return runEvaluator("checkAll", compilerEvaluator,
            e -> translateCheckResults((IList) e.call("checkAll", folder, pcfg.asConstructor())),
            Collections.emptyMap(), exec
            );
    }

    private static Map<ISourceLocation,ISet> translateCheckResults(IList messages) {
        return messages.stream()
            .filter(IConstructor.class::isInstance)
            .map(IConstructor.class::cast)
            .collect(Collectors.toMap(
                c -> (ISourceLocation) c.get("src"),
                c -> (ISet) c.get("messages")));
    }

    public InterruptibleFuture<Map<ISourceLocation, ISet>> compileFile(ISourceLocation file, PathConfig pcfg, Executor exec) {
        return compileFileList(VF.list(file), pcfg, exec);
    }

    private Map<ISourceLocation, ISet> buildEmptyResult(IList files) {
        return files.stream()
            .map(ISourceLocation.class::cast)
            .collect(Collectors.toMap(k -> k, k -> VF.set()));
    }

    public InterruptibleFuture<Map<ISourceLocation, ISet>> compileFileList(IList files, PathConfig pcfg, Executor exec) {
        return runEvaluator("check", compilerEvaluator,
            e -> translateCheckResults((IList) e.call("check", files, pcfg.asConstructor())),
            buildEmptyResult(files), exec
            );
    }


    private ISourceLocation getFileLoc(ITree moduleTree) {
        try {
            if (TreeAdapter.isTop(moduleTree)) {
                moduleTree = TreeAdapter.getStartTop(moduleTree);
            }
            ISourceLocation loc = TreeAdapter.getLocation(moduleTree);
            if (loc != null) {
                return loc.top();
            }
            return null;
        } catch (Exception t) {
            logger.trace("Failure to get file loc from tree: {}", moduleTree, t);
            return null;
        }
    }

    private final static INode EMPTY_NODE = VF.node("");

    public InterruptibleFuture<INode> getOutline(IConstructor module, Executor exec) {
        ISourceLocation loc = getFileLoc((ITree) module);
        if (loc == null) {
            return new InterruptibleFuture<>(CompletableFuture.completedFuture(EMPTY_NODE), () -> {});
        }

        return runEvaluator("outline", outlineEvaluator, eval -> (INode) eval.call("outline", module), EMPTY_NODE, exec);
    }

    private static <T> InterruptibleFuture<T> runEvaluator(String task, Future<Evaluator> eval, Function<Evaluator, T> call,
        T defaultResult, Executor exec) {
        AtomicBoolean interrupted = new AtomicBoolean(false);
        AtomicReference<@Nullable Evaluator> runningEvaluator = new AtomicReference<>(null);
        return new InterruptibleFuture<>(CompletableFuture.supplyAsync(() -> {
            try {
                Evaluator actualEval = eval.get();

                synchronized (actualEval) {
                    try {
                        runningEvaluator.set(actualEval);
                        if (interrupted.get()) {
                            return defaultResult;
                        }
                        return call.apply(actualEval);
                    } catch (InterruptException e) {
                        return defaultResult;
                    } finally {
                        actualEval.__setInterrupt(false);
                        runningEvaluator.set(null);
                    }
                }
            } catch (Throw e) {
                logger.error("Internal error during {}\n{}: {}\n{}", task, e.getLocation(), e.getMessage(),
                    e.getTrace());
                logger.error("Full internal error: ", e);
                return defaultResult;
            } catch (Exception e) {
                logger.error(task + " failed", e);
                return defaultResult;
            }
        }, exec),
        () -> {
            interrupted.set(true);
            Evaluator actualEval = runningEvaluator.get();
            if (actualEval != null) {
                actualEval.interrupt();
            }
        });
    }

    private Future<Evaluator> makeFutureEvaluator(String label, final String... imports) {
        return asyncGenerator(label, () -> {
            logger.debug("Creating evaluator: {}", label);
            Logger customLog = LogManager.getLogger("Evaluator: " + label);
            Evaluator eval = ShellEvaluatorFactory.getDefaultEvaluator(
                new ByteArrayInputStream(new byte[0]),
                IoBuilder.forLogger(customLog).setLevel(Level.INFO).buildOutputStream(),
                IoBuilder.forLogger(customLog).setLevel(Level.ERROR).buildOutputStream());
            eval.setMonitor(new LoggingMonitor(customLog));

            eval.getConfiguration().setRascalJavaClassPathProperty(System.getProperty("rascal.compilerClasspath"));
            eval.addClassLoader(RascalLanguageServer.class.getClassLoader());
            eval.addClassLoader(IValue.class.getClassLoader());
            eval.addRascalSearchPath(URIUtil.correctLocation("lib", "typepal", ""));
            eval.addRascalSearchPath(URIUtil.correctLocation("lib", "rascal-core", ""));

            for (String i : imports) {
                try {
                    eval.doImport(eval, i);
                } catch (Exception e) {
                    logger.error("Failed to import: {}", i, e);
                    throw new RuntimeException("Failure to import required module " + i, e);
                }
            }
            logger.debug("Finished creating evaluator: {}", label);

            return eval;
        });
    }


    private static <T> Future<T> asyncGenerator(String name, Callable<T> generate) {
        FutureTask<T> result = new FutureTask<>(() -> {
            try {
                return generate.call();
            } catch (Exception e) {
                throw new RuntimeException("Cannot initialize " + name, e);
            }
        });

        Thread t = new Thread(result, "Loading " + name + " Evaluator");
        t.setDaemon(true);
        t.start();

        return result;
    }

    public ITree parseSourceFile(ISourceLocation loc, String input) {
        return parseContents(loc, input.toCharArray());
    }

    public ITree parseSourceFile(ISourceLocation loc) throws IOException {
        return parseContents(loc, getResourceContent(loc));
    }

    private ITree parseContents(ISourceLocation loc, char[] input) {
        IActionExecutor<ITree> actions = new NoActionExecutor();
        return new RascalParser().parse(Parser.START_MODULE, loc.getURI(), input, actions,
            new DefaultNodeFlattener<>(), new UPTRNodeFactory(true));
    }

    private static final char[] getResourceContent(ISourceLocation location) throws IOException {
        try (Reader textStream = URIResolverRegistry.getInstance().getCharacterReader(location)) {
            return InputConverter.toChar(textStream);
        }
    }
}