package com.morganNilsson.app.editor;

public class AppendBuffer {
    static String buffer;

    static public void append(AppendBuffer ab, final String s){
        // append  to the appendBuffer 
        buffer += s;
    }
    
    static public void free(AppendBuffer ab){
        buffer = "";
    }

    static public void free(){
        buffer = "";
    }
}
