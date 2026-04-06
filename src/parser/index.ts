import {
  CharStream,
  CommonTokenStream,
  BaseErrorListener,
  type RecognitionException,
  type Recognizer,
  type Token,
  type ATNSimulator,
} from "antlr4ng";
import { SpecLexer } from "#parser/generated/SpecLexer.js";
import { SpecParser, type SpecFileContext } from "#parser/generated/SpecParser.js";

export interface ParseError {
  line: number;
  column: number;
  message: string;
}

export interface ParseResult {
  tree: SpecFileContext;
  errors: ParseError[];
}

class CollectingErrorListener extends BaseErrorListener {
  constructor(private errors: ParseError[]) {
    super();
  }

  override syntaxError<S extends Token, T extends ATNSimulator>(
    _recognizer: Recognizer<T>,
    _offendingSymbol: S | null,
    line: number,
    column: number,
    msg: string,
    _e: RecognitionException | null,
  ): void {
    this.errors.push({ line, column, message: msg });
  }
}

export function parseSpec(input: string): ParseResult {
  const chars = CharStream.fromString(input);
  const lexer = new SpecLexer(chars);
  const tokens = new CommonTokenStream(lexer);
  const parser = new SpecParser(tokens);

  const errors: ParseError[] = [];
  lexer.removeErrorListeners();
  parser.removeErrorListeners();
  const listener = new CollectingErrorListener(errors);
  lexer.addErrorListener(listener);
  parser.addErrorListener(listener);

  const tree = parser.specFile();
  return { tree, errors };
}
