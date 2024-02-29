package com.morganNilsson.app.editor;

import org.jline.terminal.Terminal;

public class EditorConfig {
        // cursor position
        public static int cx, cy;
        public static int rx;
        // screen offsets for moving cursor off screen
        public static int rowOffset;
        public static int columnOffset;
        // default terminal settings
        public static Terminal orig_terminal;
        public static int dirty;
        // rows and columns of the terminal
        public static int screenRows, screenColumns;
        public static int displayLength;
        public static EditorRow row;
        public static char[] fileName;
        public static EditorSyntax[] syntax;
        public static char[] statusMessage = new char[80];
        public static int statusMessage_time; // time_t
}
