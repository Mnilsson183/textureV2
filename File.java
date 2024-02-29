public class File {
    public static void save(){
        if(fileName == null){
            fileName = editorPrompt("Save as (Esc to cancel): %s", null);
            if(fileName == null){
                editorSetStatusMessage("Save aborted");
                return;
            }
            editorSelectSyntaxHighlight();
        }
    
        int length;
        String buffer = editorRowsToString(length);
        int fd = open(fileName, O_RDWR | O_CREAT, 0644);
        if (fd != -1){
            if(ftruncate(fd, length) != -1){
                if(write(fd, buffer, length) == length){
                    close(fd);
                    free(buffer);
                    dirty = 0;
                    editorSetStatusMessage("%d bytes written to disk", length);
                    return;
                }
            }
            close(fd);
        }
        free(buffer);
        setStatusMessage("Can't save! I/O error");
    }

    public static void findCallback(String query, int key){
        static int last_match = -1;
        static int direction = 1;
    
        static int saved_highLight_line;
        static char *saved_highLight = NULL;
    
        if(saved_highLight){
            memcpy(row[saved_highLight_line].highLight, saved_highLight, row[saved_highLight_line].renderSize);
            free(saved_highLight);
            saved_highLight = NULL;
        }
    
        if(key == '\r' || key == '\u001b'){
            last_match = -1;
            direction = 1;
            return;
        } else if(key == ARROW_RIGHT || key == ARROW_DOWN){
            direction = 1;
        } else if(key == ARROW_LEFT || key == ARROW_UP){
            direction = -1;
        } else{
            last_match = -1;
            direction = 1;
        }
    
        if(last_match == -1){
            direction = 1;
        }
        int current = last_match;
        int i;
        for(i = 0; i < displayLength; i++){
            current += direction;
            if(current == -1){
                current = displayLength - 1;
            } else if(current == displayLength){
                current = 0;
            }
    
            EditorRow row = row[current];
            String match = strstr(row.render, query);
            if(match){
                last_match = current;
                cy = current;
                cx = editorRowRxToCx(row, match - row.render);
                rowOffset = displayLength;
    
                saved_highLight_line = current;
                saved_highLight = malloc(row.size);
                memcpy(saved_highLight, row.highLight, row.renderSize);
                memset(row.highLight[match - row.render], HL_MATCH, strlen(query));
                break;
            }
        }    
    }

    public static void processKeyPress(){
        static int quit_times = TEXTURE_QUIT_TIMES;
    
        int c = readKey();
    
        switch (c){
            case '\r':
                insertNewLine();
                break;
    
            // exit case
            case CTRL_KEY('q'):
                if(dirty != 0 && quit_times > 0){
                    setStatusMessage("WARNING!! file has unsaved changes. "
                    "Press Ctrl-Q %d more times to quit", quit_times);
                    quit_times--;
                    return;
                }
                // write the 4 byte erase in display to the screen
                write(STDOUT_FILENO, "\u001b[2J", 4);
                // move the cursor to the 1,1 position in the terminal
                write(STDOUT_FILENO, "\u001b[H", 3);
                System.exit(1);
                break;
    
            case CTRL_KEY('s'):
                save();
                break;
            // home key sets the x position to the home 
            case HOME_KEY:
                E.cx = 0;
                break;
            // end key sets the x position to the column before the end of the screen
            case END_KEY:
                if (E.cy < E.displayLength){
                    E.cx = E.row[E.cy].size;
                }
                break;
    
            case CTRL_KEY('f'):
                if(E.cy < E.displayLength){
                    editorFind();
                }
                break;
    
            case BACKSPACE:
            case CTRL_KEY('h'):
            case DEL_KEY:
                if(c == DEL_KEY){
                    editorMoveCursor(ARROW_RIGHT);
                }
                editorDeleteChar();
                break;
    
    
            // send the cursor to the top of the column in cases up and down
            case PAGE_UP:
            case PAGE_DOWN:
                {
                    if (c == PAGE_UP){
                        E.cy = E.rowOffset;
                    } else if(c == PAGE_DOWN){
                        E.cy = E.rowOffset + E.screenRows - 1;
                    }
    
                    if (E.cy > E.displayLength){
                        E.cy = E.displayLength;
                    }
    
                    int times = E.screenRows;
                    while(times--){
                        editorMoveCursor(c == PAGE_UP ? ARROW_UP : ARROW_DOWN);
                    }
                }
                break;
    
            case ARROW_UP:
            case ARROW_DOWN:
            case ARROW_LEFT:
            case ARROW_RIGHT:
                editorMoveCursor(c);
                break;
    
            case CTRL_KEY('l'):
            case '\u001b':
                break;
    
            default:
                editorInsertChar(c);
                break;
        }
        quit_times = TEXTURE_QUIT_TIMES;
    }
}
