/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.rascalmpl.vscode.lsp;

import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageServer;
import org.rascalmpl.uri.URIUtil;
import org.rascalmpl.vscode.lsp.terminal.ITerminalIDEServer.LanguageParameter;

import io.usethesource.vallang.ISourceLocation;

public interface IBaseLanguageServerExtensions  extends LanguageServer {
    @JsonRequest("rascal/supplyIDEServicesConfiguration")
    default CompletableFuture<IDEServicesConfiguration> supplyIDEServicesConfiguration() {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/sendRegisterLanguage")
    default CompletableFuture<Void> sendRegisterLanguage(LanguageParameter lang) {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/supplyProjectCompilationClasspath")
    default CompletableFuture<String[]> supplyProjectCompilationClasspath(URIParameter projectFolder) {
        throw new UnsupportedOperationException();
    }

    public static class URIParameter {
        private String uri;

        public URIParameter(String uri) {
            this.uri = uri;
        }

        public String getUri() {
            return uri;
        }

        public ISourceLocation getLocation() throws URISyntaxException {
            return URIUtil.createFromURI(uri);
        }
    }

}
