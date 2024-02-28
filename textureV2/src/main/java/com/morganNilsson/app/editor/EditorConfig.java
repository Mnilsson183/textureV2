package com.morganNilsson.app.editor;

import org.jline.terminal.Terminal;

public class EditorConfig {
        // cursor position
        int cx, cy;
        int rx;
        // screen offsets for moving cursor off screen
        int rowOffset;
        int columnOffset;
        // default terminal settings
        Terminal orig_terminal;
        int dirty;
        // rows and columns of the terminal
        int screenRows, screenColumns;
        int displayLength;
        EditorRow[] row;
        char[] fileName;
        EditorSyntax[] syntax;
        char[] statusMessage = new char[80];
        time_t statusMessage_time;
}
