package com.morganNilsson.Editor;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;


public class Editor {
    static void enableRawMode(){
        // function to enter raw mode of the terminal
        try {
            Terminal terminal = TerminalBuilder.terminal();
            LineReader lineReader = LineReaderBuilder.builder().terminal(terminal).build();
            State.terminal = terminal;
            terminal.enterRawMode();
        } catch (Exception e) {
            // TODO: handle exception
        }
    }
}
