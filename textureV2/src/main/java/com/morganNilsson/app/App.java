package com.morganNilsson.app;

import com.morganNilsson.app.editor.*;

public class App 
{
    public static void main( String[] args )
    {
        EditorConfig.
        
        System.out.println("Hello World!");
        Editor.enableRawMode();
        Editor E = new Editor();
    // check the passed number of args
    if (args.length >= 2){
        Editor.open(args[1]);
    }

    Editor.setStatusMessage("HELP: Ctrl-q to quit | Ctrl-s to save | Ctrl-f find");
    
    while (true){
        Editor.RefreshScreen();
        Editor.ProcessKeyPress();
    }
    }
}
