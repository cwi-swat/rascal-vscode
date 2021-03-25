@license{
  Copyright (c) 2021 NWO-I Centrum Wiskunde & Informatica
  All rights reserved. This program and the accompanying materials
  are made available under the terms of the Eclipse Public License v1.0
  which accompanies this distribution, and is available at
  http://www.eclipse.org/legal/epl-v10.html
}
@contributor{Jurgen J. Vinju - Jurge1n.Vinju@cwi.nl - CWI}
module util::LanguageServer

import util::Reflective;
// extend Content;
import ParseTree;

data Language
    = language(PathConfig pcfg, str name, str extension, str mainModule, str mainFunction);

alias Parser        = Tree (str /*input*/, loc /*origin*/);
alias Summarizer    = Summary (loc /*origin*/, Tree /*input*/);
alias Outliner      = list[DocumentSymbol] (Tree /*input*/);
alias Annotater     = Tree (Tree /*input*/);
alias Completer     = list[Completion] (Tree /*input*/, str /*prefix*/, int /*requestOffset*/);
alias Builder       = list[Message] (list[loc] /*sources*/, PathConfig /*pcfg*/);

@synopsis{Each kind of service contibutes the implementation of one (or several) IDE features.}
data LanguageService
    = parser(Parser parser)
    | summarizer(Summarizer summarizer)
    | outliner(Outliner outliner)
    | completer(Completer completer)
    | builder(Builder builder)
    | command(Command command)
    ;

@synopsis{Annotations that an annotator may provide on a parse tree node}
data Tree(
    set[Message] messages        = {}, // error messages associated with a tree node
    set[str]     documentation   = {}, // this node is documented with this string
    set[loc]     definitions     = {}, // this use is defined there
    set[loc]     references      = {}, // this declaration is referenced there
    set[loc]     implementations = {}  // this definition is implemented there
);

@synopsis{A model encodes all IDE-relevant information about a single source file.}
data Summary = summary(loc src,
    rel[loc, Message] messages = {},
    rel[loc, str]     documentation = {},   // documentation for each location
    rel[loc, loc]     definitions = {},     // links to the definitions of names
    rel[loc, loc]     references = {},      // links to the uses of definitions
    rel[loc, loc]     implementations = {}  // links to the implementations of declarations
);

data Completion = completion(str newText, str proposal=newText);

@synopsis{produces a summarizer from an annotator by collecting all relevant information from a source Tree}
LanguageService summarizer(Annotater annotater) = summarizer(Summary (loc src, Tree input) {
    messages = {};
    documentation = {};
    references = {};
    definitions = {};
    implementations = {};

    visit(annotater(input)) {
        case Tree t: if (t.src?) {
            messages        += {<t.src, m> | m <- t.messages};
            documentation   += {<t.src, d> | d <- t.documentation};
            definitions     += {<t.src, r> | r <- t.definitions};
            references      += {<t.src, r> | r <- t.references};
            implementations += {<t.src, r> | r <- t.implementations};
        }
    }

    return summary(src,
        messages=messages,
        documentation=documentation,
        references=references,
        definitions=definitions,
        implementations=implementations
    );
});

// THERE is a bug in the interpreter that lets this function fail
// @synopsis{Produces a parser service from a reified grammar}
// Contribution parserFor(type[Tree] grammar) = parser(Tree (str input, loc src) {
//     return parse(grammar, input, src);
// });

@synopsis{DocumentSymbol encodes a sorted and hierarchical outline of a source file}
data DocumentSymbol
    = symbol(
        str name,
        DocumentSymbolKind kind,
        loc range,
        loc selection=range,
        str detail="",
        list[DocumentSymbol] children=[]
    );

data DocumentSymbolKind
	= \file()
	| \module()
	| \namespace()
	| \package()
	| \class()
	| \method()
	| \property()
	| \field()
	| \constructor()
	| \enum()
	| \interface()
	| \function()
	| \variable()
	| \constant()
	| \string()
	| \number()
	| \boolean()
	| \array()
	| \object()
	| \key()
	| \null()
	| \enumMember()
	| \struct()
	| \event()
	| \operator()
	| \typeParameter()
    ;

data DocumentSymbolTag
    = \deprecated()
    ;

data CompletionProposal = sourceProposal(str newText, str proposal=newText);

data Command
    = action(str label, void (Tree tree, loc selection) action)
    // | interaction(str label, Content (Tree tree, loc selection) server)
    | action(str label, void (str selStr, loc selLoc) handler)
    | toggle(str label, bool() state, void(Tree tree, loc selection) action)
    | edit(str label, str (Tree tree, loc selection) edit)
    | group(str label, list[Command] members)
    | menu(str label, list[Command] members)
    | popup(str label, list[Command] members)
    ;

@javaClass{org.rascalmpl.vscode.lsp.parametric.RascalInterface}
java void registerLanguage(Language lang);
