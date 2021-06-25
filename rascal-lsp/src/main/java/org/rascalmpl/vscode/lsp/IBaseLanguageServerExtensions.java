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
package org.rascalmpl.vscode.lsp;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
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

    // BELOW THE FILESYSTEM SERVICE METHODS:

    @JsonRequest("rascal/filesystem/schemes")
    default CompletableFuture<String[]> fileSystemSchemes() {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/filesystem/watch")
    default CompletableFuture<Void> watch(WatchParameters params) throws IOException, URISyntaxException {
        throw new UnsupportedOperationException();
    }

    @JsonNotification("rascal/filesystem/onDidChangeFile")
    default void onDidChangeFile(FileChangeEvent event) { };

    @JsonRequest("rascal/filesystem/stat")
    default CompletableFuture<FileStat> stat(URIParameter uri) throws IOException, URISyntaxException {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/filesystem/readDirectory")
    default CompletableFuture<FileWithType[]> readDirectory(URIParameter uri) throws URISyntaxException, IOException {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/filesystem/createDirectory")
    default CompletableFuture<Void> createDirectory(URIParameter uri) throws IOException, URISyntaxException {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/filesystem/readFile")
    default CompletableFuture<LocationContent> readFile(URIParameter uri) throws URISyntaxException {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/filesystem/writeFile")
    default CompletableFuture<Void>  writeFile(WriteFileParameters params) throws URISyntaxException, IOException {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/filesystem/delete")
    default CompletableFuture<Void>  delete(DeleteParameters params) throws IOException, URISyntaxException {
        throw new UnsupportedOperationException();
    }

    @JsonRequest("rascal/filesystem/rename")
    default CompletableFuture<Void>  rename(RenameParameters params) throws IOException, URISyntaxException {
        throw new UnsupportedOperationException();
    }

    public static class DeleteParameters {
        private final String uri;
        private final boolean recursive;

        public DeleteParameters(String uri, boolean recursive) {
            this.uri = uri;
            this.recursive = recursive;
        }

        public ISourceLocation getLocation() throws URISyntaxException {
            return new URIParameter(uri).getLocation();
        }

        public boolean isRecursive() {
            return recursive;
        }
    }

    public static class RenameParameters {
        private final String oldUri;
        private final String newUri;
        private final boolean overwrite;

        public RenameParameters(String oldUri, String newUri, boolean overwrite) {
            this.oldUri = oldUri;
            this.newUri = newUri;
            this.overwrite = overwrite;
        }

        public ISourceLocation getOldLocation() throws URISyntaxException {
            return new URIParameter(oldUri).getLocation();
        }

        public ISourceLocation getNewLocation() throws URISyntaxException {
            return new URIParameter(newUri).getLocation();
        }

        public boolean isOverwrite() {
            return overwrite;
        }
    }
    public static class WatchParameters {
        private final String uri;
        private final boolean recursive;
        private final String[] excludes;

        public WatchParameters(String uri, boolean recursive, String[] excludes) {
            this.uri = uri;
            this.recursive = recursive;
            this.excludes = excludes;
        }

        public ISourceLocation getLocation() throws URISyntaxException {
            return new URIParameter(uri).getLocation();
        }

        public String[] getExcludes() {
            return excludes;
        }

        public boolean isRecursive() {
            return recursive;
        }
    }
    public static class FileChangeEvent {
        private final FileChangeType type;
        private final String uri;

        public FileChangeEvent(FileChangeType type, String uri) {
            this.type = type;
            this.uri = uri;
        }

        public FileChangeType getType() {
            return type;
        }

        public ISourceLocation getLocation() throws URISyntaxException {
            return new URIParameter(uri).getLocation();
        }
    }

    public static enum FileChangeType {
        Changed(1),
        Created(2),
        Deleted(3);

        private final int value;

        private FileChangeType(int val) {
            assert val == 1 || val == 2 || val == 3;
            this.value = val;
        }

        public int getValue() {
            return value;
        }
    };
    public static class FileStat {
        FileType type;
        long ctime;
        long mtime;
        long size;

        public FileStat(FileType type, long ctime, long mtime, long size) {
            this.type = type;
            this.ctime = ctime;
            this.mtime = mtime;
            this.size = size;
        }
    }

    public static enum FileType {
        Unknown(0),
        File(1),
        Directory(2),
        SymbolicLink(64);

        private final int value;

        private FileType(int val) {
            assert val == 0 || val == 1 || val == 2 || val == 64;
            this.value = val;
        }

        public int getValue() {
            return value;
        }
    };
    public static class FileWithType {
        private final String name;
        private final FileType type;

        public FileWithType(String name, FileType type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public FileType getType() {
            return type;
        }
    }

    public static class LocationContent {
        private String content;

        public LocationContent(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }
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

    public static class WriteFileParameters {
        private final String uri;
        private final String content;
        private final boolean create;
        private final boolean overwrite;

        public WriteFileParameters(String uri, String content, boolean create, boolean overwrite) {
            this.uri = uri;
            this.content = content;
            this.create = create;
            this.overwrite = overwrite;
        }

        public String getUri() {
            return uri;
        }

        public ISourceLocation getLocation() throws URISyntaxException {
            return new URIParameter(uri).getLocation();
        }

        public String getContent() {
            return content;
        }

        public boolean isCreate() {
            return create;
        }

        public boolean isOverwrite() {
            return overwrite;
        }
    }
}
