/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import * as path from 'path';
import * as net from 'net';
import * as cp from 'child_process';
import * as os from 'os';

import {LanguageClient, LanguageClientOptions, ServerOptions, StreamInfo, integer } from 'vscode-languageclient/node';
import { TextDocumentContentProvider } from 'vscode';
import { RascalTerminalLinkProvider } from './RascalTerminalLinkProvider';

const deployMode = (process.env.RASCAL_LSP_DEV || "false") !== "true";
const ALL_LANGUAGES_ID = 'parametric-rascalmpl';
const registeredFileExtensions:Set<string> = new Set();

let childProcess: cp.ChildProcessWithoutNullStreams;

class IDEServicesConfiguration {
    public port:integer;

    constructor (port:integer) {
        this.port = port;
    }
}

export function getRascalExtensionDeploymode() : boolean {
    return deployMode;
}

export function activate(context: vscode.ExtensionContext) {
    const parametricClient = activateParametricLanguageClient(context);
    const rascalClient = activateRascalLanguageClient(context, parametricClient);
    registerTerminalCommand(context, rascalClient);

    context.subscriptions.push(vscode.workspace.onDidOpenTextDocument(e => {
        const ext = path.extname(e.fileName);

        if (ext !== "" && e.languageId !== ALL_LANGUAGES_ID && registeredFileExtensions.has(ext.substring(1))) {
            vscode.languages.setTextDocumentLanguage(e, ALL_LANGUAGES_ID);
        }
    }));

    const provider = new RascalTextDocumentContentProvider(rascalClient);

    vscode.workspace.registerTextDocumentContentProvider('project', provider);
    vscode.workspace.registerTextDocumentContentProvider('std', provider);
    vscode.workspace.registerTextDocumentContentProvider('lib', provider);

    vscode.window.registerTerminalLinkProvider(new RascalTerminalLinkProvider());

    console.log('LSP servers started (Rascal and Parametric)');
}

class RascalTextDocumentContentProvider implements TextDocumentContentProvider {
    onDidChange?: vscode.Event<vscode.Uri>;
    client:LanguageClient;

    constructor(client:LanguageClient) {
        this.client = client;
    }

    provideTextDocumentContent(uri: vscode.Uri, token: vscode.CancellationToken): vscode.ProviderResult<string> {
        return this.client
            .sendRequest<LocationContent>("rascal/locationContents", { uri: uri.toString() })
            .then(c => c.content);
    }
}

export function registerLanguage(context: vscode.ExtensionContext, client:LanguageClient, lang:LanguageParameter) {
    // first we load the new language into the parametric server
    client.sendRequest("rascal/sendRegisterLanguage", lang);

    if (lang.extension && lang.extension !== "") {
        registeredFileExtensions.add(lang.extension);
    }
}

export function activateRascalLanguageClient(context: vscode.ExtensionContext, parametricServer:LanguageClient):LanguageClient {
    return activateLanguageClient(context, 'rascalmpl', 'org.rascalmpl.vscode.lsp.rascal.RascalLanguageServer', 'Rascal MPL Language Server', 8888, parametricServer);
}

export function activateParametricLanguageClient(context: vscode.ExtensionContext) {
    return activateLanguageClient(context, 'parametric-rascalmpl', 'org.rascalmpl.vscode.lsp.parametric.ParametricLanguageServer', 'Language Parametric Rascal Language Server', 9999);
}

export function activateLanguageClient(context: vscode.ExtensionContext, language:string, main:string, title:string, devPort:integer, parametricServer?:LanguageClient) :LanguageClient {
    const serverOptions: ServerOptions = deployMode
        ? buildRascalServerOptions(context, main)
        : () => connectToRascalLanguageServerSocket(devPort) // we assume a server is running in debug mode
            .then((socket) => <StreamInfo> { writer: socket, reader: socket});

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: language }],
    };

    const client = new LanguageClient(language, title, serverOptions, clientOptions, true);

    client.onReady().then(() => {
        client.onNotification("rascal/showContent", (bp:BrowseParameter) => {
           showContentPanel(bp.uri);
        });

        if (parametricServer) {
            client.onNotification("rascal/receiveRegisterLanguage", (lang:LanguageParameter) => {
                registerLanguage(context, parametricServer, lang);
            });
        }
    });

    context.subscriptions.push(client.start());

    return client;
}


export function deactivate() {
    if (childProcess) {
        childProcess.kill('SIGKILL');
    }
}

function registerTerminalCommand(context: vscode.ExtensionContext, client:LanguageClient) {
    let disposable = vscode.commands.registerCommand('rascalmpl.createTerminal', () => {
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            return;
        }

        const document = editor.document;
        if (!document) {
            return;
        }

        const uri = document.uri;
        if (!uri) {
            return;
        }

        const reply:Promise<IDEServicesConfiguration> = client.sendRequest("rascal/supplyIDEServicesConfiguration");

        reply.then(cfg => {
            const reply:Promise<Array<string>> = client.sendRequest("rascal/supplyProjectCompilationClasspath", {uri: uri.toString()});

            reply.then(cp => {
                // vscode.window.showInformationMessage('hello', cp.join(':'));
                let terminal = vscode.window.createTerminal({
                    cwd: path.dirname(uri.fsPath),
                    shellPath: getJavaExecutable(),
                    shellArgs: [
                        '-cp' , buildJVMPath(context) + (cp.length > 0 ? (path.delimiter + cp.join(path.delimiter)) : ''),
                        'org.rascalmpl.vscode.lsp.terminal.LSPTerminalREPL',
                        '--ideServicesPort',
                        '' + cfg.port
                    ],
                    name: 'Rascal Terminal',
                });

                context.subscriptions.push(disposable);
                terminal.show(false);
            });
        });
    });
}

function gb(amount: integer) {
    return amount * (1024 * 1024);
}

function calculateRascalMemoryReservation() {
    // rascal lsp needs at least 800M but runs better with 2G or even 2.5G (especially the type checker)
    if (os.totalmem() >= gb(32)) {
        return "-Xmx2500M";
    }
    if (os.totalmem() >= gb(16)) {
        return "-Xmx1500M";

    }
    if (os.totalmem() >= gb(8)) {
        return "-Xmx1200M";
    }
    return "-Xmx800M";
}

function calculateDSLMemoryReservation() {
    // this is a hard one, if you register many DSLs, it can grow quite a bit
    // 400MB per language is a reasonable estimate (for average sized languages)
    if (os.totalmem() >= gb(32)) {
        return "-Xmx2400M";
    }
    if (os.totalmem() >= gb(16)) {
        return "-Xmx1600M";
    }
    if (os.totalmem() >= gb(8)) {
        return "-Xmx1200M";
    }
    return "-Xmx800M";

}

function buildJVMPath(context: vscode.ExtensionContext) :string {
    const jars = ['rascal-lsp.jar', 'rascal.jar', 'rascal-core.jar', 'typepal.jar'];
    return jars.map(j => context.asAbsolutePath(path.join('.', 'assets', 'jars', j))).join(path.delimiter);
}

function buildRascalServerOptions(context: vscode.ExtensionContext, main:string): ServerOptions {
    const classpath = buildJVMPath(context);
    return {
        command: 'java',
        args: ['-Dlog4j2.configurationFactory=org.rascalmpl.vscode.lsp.LogRedirectConfiguration', '-Dlog4j2.level=DEBUG',
            '-Drascal.lsp.deploy=true', '-Drascal.compilerClasspath=' + classpath,
            main.includes("Parametric") ? calculateDSLMemoryReservation() : calculateRascalMemoryReservation(),
            '-cp', classpath, main],
    };
}

function getJavaExecutable():string {
    const { JAVA_HOME } = process.env;

    const name = os.platform() === 'win32' ? 'java.exe' : 'java';
    return JAVA_HOME ? path.join(JAVA_HOME, 'bin', name) : name;
}

function connectToRascalLanguageServerSocket(port: number): Promise<net.Socket> {
    return new Promise((connected, failed) => {
        const maxTries = 20;
        const host = '127.0.0.1';
        let retryDelay = 0;
        const client = new net.Socket();
        var tries = 0;

        function retry(err?: Error) : net.Socket | void {
            if (tries <= maxTries) {
                setTimeout (() => {
                    tries++;
                    retryDelay = Math.min(2500, retryDelay + 250);
                    client.connect(port, host);
                }, retryDelay);
            }
            else {
                return failed("Connection retries exceeded" + (err ? (": " + err.message) : ""));
            }
        }

        client.setTimeout(1000);
        client.on('timeout', retry);
        client.on('error', retry);
        client.once('connect', () => {
            client.setTimeout(0);
            client.removeAllListeners();
            return connected(client);
        });

        return retry();
    });
}

let contentPanels : vscode.WebviewPanel[] = [];

function showContentPanel(url: string) : void {
    let oldPanel:vscode.WebviewPanel|undefined = contentPanels.find(p => (p as vscode.WebviewPanel).title === url);

    if (oldPanel) {
        oldPanel.dispose();
    }

    const panel = vscode.window.createWebviewPanel(
            "text/html",
            url,
            vscode.ViewColumn.One,
            {
                    enableScripts: true,
            }
    );

    contentPanels.push(panel);
    loadURLintoPanel(panel, url);

    panel.onDidDispose((e) => {
            contentPanels.splice(contentPanels.indexOf(panel), 1);
            // TODO: possibly clean up the server side while we are at it?
    });
}

function loadURLintoPanel(panel:vscode.WebviewPanel, url:string): void {
    panel.webview.html = `
            <!DOCTYPE html>
            <html lang="en">
            <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body>
            <iframe
                id="iframe-rascal-content"
                src="${url}"
                frameborder="0"
                sandbox="allow-scripts allow-forms allow-same-origin allow-pointer-lock allow-downloads allow-top-navigation"
                style="display: block; margin: 0px; overflow: hidden; position: absolute; width: 100%; height: 100%; visibility: visible;"
            >
            Loading ${url}...
            </iframe>
            </body>
            </html>`;
}

interface BrowseParameter {
    uri: string;
    mimetype: string;
    title:string;
}

interface URIParameter {
    uri:string;
}

interface LanguageParameter {
    pathConfig: string 		// rascal pathConfig constructor as a string
    name: string; 			// name of the language
    extension:string; 		// extension for files in this language
    mainModule: string; 	// main module to locate mainFunction in
    mainFunction: string; 	// main function which contributes the language implementation
}

interface LocationContent {
    content: string;
}
