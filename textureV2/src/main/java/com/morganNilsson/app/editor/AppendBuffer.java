package com.morganNilsson.app.editor;

public class AppendBuffer {
    String buffer;

    static public void append(AppendBuffer ab, final String s){
        // append  to the appendBuffer 
        this.buffer += s;
    }
    
    static public void free(AppendBuffer ab){
        ab.buffer = "";
    }

    static public void free(){
        this.buffer = "";
    }
}
