package com.morganNilsson.app.editor;

import java.io.BufferedReader;

public class STDIN_FILENO {
    public BufferedReader reader = null;
    
    public STDIN_FILENO(){
        reader = new BufferedReader(InputStreamReader(System.in));
    }
}
