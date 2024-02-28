package com.morganNilsson.app.terminal;

public class TerminalMethods {

    public static void terminate(String s){
        // write the 4 byte erase in display to the screen
        System.out.print("\u001B[2J");
        // move the cursor to the 1,1 position in the terminal
        System.out.print("\u001B[H");
        // function to deal with the error outputs
        System.err.println(s);
        System.exit(1);
    }

    static public int getWindowSize(int[] rows, int[] columns){
        struct winsize ws;
    
        // easy way to do the getCursorPosition function
        if (ioctl(STDOUT_FILENO, TIOCGWINSZ, &ws) == -1 || ws.ws_col == 0){
            if (write(STDOUT_FILENO, "\x1b[999C\x1b[999B", 12) != 12){
                return getCursorPosition(rows, columns);
            }
            // if the easy way fails use the man one
            editorReadKey();
            return -1;
        } else{
            *columns = ws.ws_col;
            *rows = ws.ws_row;
            return 0;
        }
    }
}
