package com.morganNilsson.app.editor;

public class AppendBuffer {
    char[] b;
    int len;

    void abAppend(struct AppendBuffer *ab, const char *s, int len){
        // append  to the appendBuffer 
        // give more memory to the information field of the struct
        char* new = realloc(ab->b, ab->len + len);
    
        // error check
        if (new == NULL){
            return;
        }
        // copy the bytes of s to the end of the new data structure
        memcpy(&new[ab->len], s, len);
        // assign to the old appendBuffer struct the new values with the included information
        ab->b = new;
        ab->len += len;
    }
    
    void abFree(struct AppendBuffer *ab){
        // free the data struct
        free(ab->b);
    }
}
