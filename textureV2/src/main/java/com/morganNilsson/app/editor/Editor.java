package com.morganNilsson.app.editor;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import com.morganNilsson.app.State;
import com.morganNilsson.app.terminal.TerminalMethods;


public class Editor {

    public Editor(){
        // cursor positions
        E.cx = 0;
        E.cy = 0;
        E.rx = 0;
        E.rowOffset = 0;
        E.columnOffset = 0;
        E.displayLength = 0;
        E.dirty = 0;
        E.row = NULL;
        E.fileName = NULL;
        E.statusMessage[0] = '\0';
        E.statusMessage_time = 0;
        E.syntax = NULL;

        if (getWindowSize(&E.screenRows, &E.screenColumns) == -1){
            terminate("getWindowSize");
        }
        E.screenRows = E.screenRows - 2;
    }

    enum editorKey{
        BACKSPACE = 127,
        ARROW_LEFT = 1000,
        ARROW_RIGHT,
        ARROW_UP,
        ARROW_DOWN,
        DEL_KEY,
        HOME_KEY,
        END_KEY,
        PAGE_UP,
        PAGE_DOWN,
    };

    enum editorHighlight{
        HL_NORMAL = 0,
        HL_COMMENT,
        HL_MULTIPLE_LINE_COMMENT,
        HL_KEYWORD1,
        HL_KEYWORD2,
        HL_STRING,
        HL_NUMBER,
        HL_MATCH
    };

    String[] C_HL_extensions = {".c", ".h", ".cpp"};
    String[] C_HL_keywords = {
        "switch", "if", "while", "for", "break", "continue", "return", "else",
        "struct", "union", "typedef", "static", "enum", "class", "case",
        "int|", "long|", "double|", "float|", "char|", "unsigned|", "signed|",
        "void|",
    };

    public static void enableRawMode(){
        // function to enter raw mode of the terminal
        try {
            Terminal terminal = TerminalBuilder.terminal();
            State.terminal = terminal;
            terminal.enterRawMode();
        } catch (Exception e) {
           e.printStackTrace();
           System.exit(0);
        }
    }

    public static void disableRawMode(){
        try {
            State.terminal.close();   
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    static int readKey(){
        int nread;
        char c;
        // read each key press
        while ((nread = read(STDIN_FILENO, &c, 1)) != 1) {
            if (nread == -1 && errno != EAGAIN){
                TerminalMethods.terminate("read");
            }
        }
        // first char is the first char of ASCII escape code
        if (c == '\u001b'){
            // sequence of 3 chars first being the ASCII escape code
            char[] seq = new char[3];
    
            // read the STDIN and set both the first and second characters in seq
            if (read(STDIN_FILENO, &seq[0], 1) != 1){
                return '\u001b';
            }
            if (read(STDIN_FILENO, &seq[1], 1) != 1){
                return '\u001b';
            }
            
            // first char being the special char
            if(seq[0] == '['){
                // check that that seq falls into the bounds of our answer
                if (seq[1] >= '0' && seq[1] <= '9'){
                    // read the STDIN to seq
                    if (read(STDIN_FILENO, &seq[2], 1) != 1){
                        return '\u001b';
                    }
                    // check if last char is ~ denoting a special charater seq
                    if (seq[2] == '~'){
                        // check the numeric value in the form of a char is equal to the following ASCII codes
                        switch (seq[1]){
                            case '1': return HOME_KEY;
                            case '3': return DEL_KEY;
                            case '4': return END_KEY;
                            case '5': return PAGE_UP;
                            case '6': return PAGE_DOWN;
                            case '7': return HOME_KEY;
                            case '8': return END_KEY;     
                        }
                    }
                // another check if the keyboard gives value of a alpha form
                } else{
                    switch (seq[1]){
                        case 'A': return ARROW_UP;
                        case 'B': return ARROW_DOWN;
                        case 'C': return ARROW_RIGHT;
                        case 'D': return ARROW_LEFT;
                        case 'H': return HOME_KEY;                      
                        case 'F': return END_KEY;
                    }
                }
            // if the value of the first char is a 0 also denoting a special charater
            } else if(seq[0] == '0'){
                switch (seq[1]){
                    case 'H': return HOME_KEY;
                    case 'F': return END_KEY;
                }
            }
            return '\u001b';
        } else {
            // if no issue each key press
            return c;
        }
    }

    int getCursorPosition(int[] rows, int[] columns){
        // man read to get the cursor position
        char[] buf = new char[32];
        int i = 0;
        // write a cursor position report
        try {
            System.out.println("\u001b[6n");
        } catch (Exception e) {
            return -1;
        }
    
        // 
        while (i < buf.length - 1){
            if (read(STDIN_FILENO, &buf[i], 1)){
                break;
            }
            if(buf[i] == 'R'){
                break;
            }
            i++;
        }
        buf[i] = '\0';
        if (buf[0] != '\u001b' || buf[1] != '['){
            return -1;
        }
        if (sscanf(&buf[2],"%d;%d", rows, columns) != 2){
            return -1;
        }
        return 0; 
    }

    void editorUpdateSyntax(EditorRow *row){
        row->highLight = realloc(row->highLight, row->renderSize);
        memset(row->highLight, HL_NORMAL, row->renderSize);
    
        if(E.syntax == NULL){
            return;
        }
    
        char **keywords = E.syntax->keywords;
    
        char *singleLightCommentStart = E.syntax->singleline_comment_start;
        char *multilineCommentStart = E.syntax->multiline_comment_start;
        char *multilineCommentEnd = E.syntax->multiline_comment_end;
    
        int singleLightCommentStartLength = singleLightCommentStart ? strlen(singleLightCommentStart): 0;
        int multilineCommentStartLength = multilineCommentStart ? strlen(multilineCommentStart) : 0;
        int multilineCommentEndLength = multilineCommentEnd ? strlen(multilineCommentEnd) : 0;
    
    
        int prevSeparator = 1;
        int in_string = 0;
        int in_comment = 0;
    
        int i = 0;
        while (i < row->renderSize){
            char c = row->render[i];
            unsigned char prevHighlight = (i > 0) ? row->highLight[i - 1] : HL_NORMAL;
    
    
            if(singleLightCommentStartLength && !in_string){
                if(!strncmp(&row->render[i], singleLightCommentStart, singleLightCommentStartLength)){
                    memset(&row->highLight[i], HL_COMMENT, row->renderSize - i);
                    break;
                }
            }
    
            if(multilineCommentStartLength && multilineCommentEndLength && !in_string){
                if(in_comment){
                    row->highLight[i] = HL_MULTIPLE_LINE_COMMENT;
                    if(!strncmp(&row->render[i], multilineCommentStart, multilineCommentStartLength)){
                        memset(&row->highLight[i], HL_MULTIPLE_LINE_COMMENT, multilineCommentStartLength);
                        i += 2;
                        in_comment = 0;
                        prevSeparator = 1;
                        continue;
                    } else{
                        i++;
                        continue;
                    }
                } else if(!strncmp(&row->render[i], multilineCommentStart, multilineCommentStartLength)){
                        memset(&row->highLight[i], HL_MULTIPLE_LINE_COMMENT, multilineCommentStartLength);
                        i += multilineCommentStartLength;
                        in_comment = 1;
                        continue;
                }
            }
    
            if(E.syntax->flags & HL_HIGHLIGHT_STRINGS){
                if(in_string){
                    if(c == '\\' && i + 1 < row->renderSize){
                        row->highLight[i + 1] = HL_STRING;
                        i += 2;
                        continue;
                    }
                    row->highLight[i] = HL_STRING;
                    if(c == '\\' && i + 1 < row->renderSize){
                        row->highLight[i + 1] = HL_STRING;
                        i += 2;
                        continue;
                    }
                    if(c == in_string){
                        in_string = 0;
                    }
                    i++;
                    prevSeparator = 1;
                    continue;
                } else{
                    if(c == '"' || c == '\''){
                        in_string = c;
                        row->highLight[i] = HL_STRING;
                        i++;
                        continue;
                    }
                }
            }
    
            if(E.syntax->flags & HL_HIGHLIGHT_NUMBERS){
                if((isdigit(c) && (prevSeparator || prevHighlight == HL_NUMBER)) || 
                (c =='.' && prevHighlight == HL_NUMBER)){
                    row->highLight[i] = HL_NUMBER;
                    i++;
                    prevSeparator = 0;
                    continue;
                }
            }
            if(prevSeparator){
                int j;
                for(j = 0; keywords[j]; j++){
                    int keywordLength = strlen(keywords[j]);
                    int keyword2 = keywords[j][keywordLength - 1] == '|';
                    if(keyword2) keywordLength--;
    
                    if(!strncmp(&row->render[i], keywords[j], keywordLength) &&
                        isSeparator(row->render[i + keywordLength])){
                            memset(&row->highLight[i], keyword2 ? HL_KEYWORD2: HL_KEYWORD1, keywordLength);
                            i+=keywordLength;
                            break;
                    }
                }
                if(keywords[j] != NULL){
                    prevSeparator = isSeparator(c);
                    i++;
                }
            }
            prevSeparator = isSeparator(c);
            i++;
        }
    }
    
    int editorSyntaxToColor(int highLight){
        switch (highLight)
        {
            case HL_COMMENT:
            case HL_MULTIPLE_LINE_COMMENT: return 36;
            case HL_KEYWORD1: return 33;
            case HL_KEYWORD2: return 32;
            case HL_NUMBER: return 31;
            case HL_STRING: return 35;
            case HL_MATCH: return 34;
            default: return 37;
        }
    }
    
    void editorSelectSyntaxHighlight(void){
        E.syntax = NULL;
        if(E.fileName == NULL){
            return;
        }
    
        char *extension = strrchr(E.fileName, '.');
    
        for(unsigned int j = 0; j < HighLightDataBase_ENTRIES; j++){
            struct EditorSyntax *s = &HighLightDataBase[j];
            unsigned int i = 0;
            while(s->fileMatch[i]){
                int is_extension = (s->fileMatch[i][0] == '0');
                if((is_extension && extension && !strcmp(extension, s->fileMatch[i])) ||
                    (!is_extension && strstr(E.fileName, s->fileMatch[i]))){
                        E.syntax = s;
    
                        int fileRow;
                        for(fileRow = 0; fileRow < E.displayLength; fileRow++){
                            editorUpdateSyntax(&E.row[fileRow]);
                        }
    
                        return;
                    }
                i++;
            }
        }
    }
    
    /* row operations */
    
    int editorRowCxToRx(EditorRow *row, int cx){
        int rx = 0;
        int j;
        for(j = 0; j < cx; j++){
            if (row->chars[j] == '\t'){
                rx += (TEXTURE_TAB_STOP - 1) - (rx % TEXTURE_TAB_STOP);
            }
            rx++;
        }
        return rx;
    }
    
    int editorRowRxToCx(EditorRow *row, int rx){
        int cur_rx = 0;
        int cx;
        for(cx = 0; cx < row->size; cx++){
            if (row->chars[cx] == '\t'){
                cur_rx += (TEXTURE_TAB_STOP - 1) - (cur_rx % TEXTURE_TAB_STOP);
            }
            cur_rx++;
            if(cur_rx > rx){
                return cx;
            }
        }
        return cx;
    }
    
    void editorUpdateRow(EditorRow *row){
        int tabs = 0;
        int j;
        for (j = 0; j < row->size; j++){
            if (row->chars[j] == '\t'){
                tabs++;
            }
        }
        free(row->render);
        row->render = malloc(row->size + ( tabs * (TEXTURE_TAB_STOP - 1)) + 1);
    
        int tempLength = 0;
        for (j = 0; j < row->size; j++){
            if (row->chars[j] == '\t'){
                row->render[tempLength++] = ' ';
                while (tempLength % TEXTURE_TAB_STOP != 0){
                    row->render[tempLength++] = ' ';
                }
            } else{
                row->render[tempLength++] = row->chars[j];
            }
        }
        row->render[tempLength] = '\0';
        row->renderSize = tempLength;
    
        editorUpdateSyntax(row);
    }
    
    void editorInsertRow(int at, char* s, size_t length){
        if(at < 0 || at > E.displayLength){
            return;
        }
    
        E.row = realloc(E.row, sizeof(EditorRow) * (E.displayLength + 1));
        memmove(&E.row[at + 1], &E.row[at], sizeof(EditorRow) * (E.displayLength - at));
    
        // add a row to display
        E.row[at].size = length;
        E.row[at].chars = malloc(length + 1);
        memcpy(E.row[at].chars, s, length);
        E.row[at].chars[length] = '\0';
    
        E.row[at].renderSize = 0;
        E.row[at].render = NULL;
        E.row[at].highLight = NULL;
        editorUpdateRow(&E.row[at]);
    
        E.displayLength++;
        E.dirty++;
    }
    
    void editorFreeRow(EditorRow *row){
        free(row->render);
        free(row->chars);
        free(row->highLight);
    }
    
    void editorDeleteRow(int at){
        if(at < 0 || at >= E.displayLength){
            return;
        }
        editorFreeRow(&E.row[at]);
        memmove(&E.row[at], &E.row[at + 1], sizeof(EditorRow) * (E.displayLength - at - 1));
        E.displayLength--;
        E.dirty++;
    }
    
    void editorRowInsertChar(EditorRow *row, int at, int c){
        if (at < 0 || at > row->size){ 
            at = row->size;
        }
        row->chars = realloc(row->chars, row->size + 2);
            memmove(&row->chars[at + 1], &row->chars[at], row->size - at + 1);
            row->size++;
            row->chars[at] = c;
            editorUpdateRow(row);
            E.dirty++;
    }
    
    void editorRowAppendString(EditorRow *row, char *s, size_t length){
        row->chars = realloc(row->chars, row->size + length + 1);
        memcpy(&row->chars[row->size], s, length);
        row->size += length;
        row->chars[row->size] = '\0';
        editorUpdateRow(row);
        E.dirty++;
    }
    
    void editorRowDeleteChar(EditorRow *row, int at){
        if (at < 0 || at >= row->size){
            return;
        }
        memmove(&row->chars[at], &row->chars[at + 1], row->size - at);
        row->size--;
        editorUpdateRow(row);
        E.dirty++;
    }
    
    /* Editor Functions */
    void editorInsertChar(int c){
        if (E.cy == E.displayLength){
            editorInsertRow(E.displayLength, "", 0);
        }
        editorRowInsertChar(&E.row[E.cy], E.cx, c);
        E.cx++;
    }
    
    void editorInsertNewLine(){
        if(E.cx == 0){
            editorInsertRow(E.cy, "", 0);
        } else{
            EditorRow *row = &E.row[E.cy];
            editorInsertRow(E.cy + 1, &row->chars[E.cx], row->size - E.cx);
            row = &E.row[E.cy];
            row->size = E.cx;
            row->chars[row->size] = '\0';
            editorUpdateRow(row);
        }
        E.cy++;
        E.cx = 0;
    }
    
    
    void editorDeleteChar(){
        if(E.cy == E.displayLength){
            return;
        }
        if(E.cx == 0 && E.cy == 0){
            return;
        }
    
        EditorRow *row = &E.row[E.cy];
    
        if(E.cx > 0){
            editorRowDeleteChar(row, E.cx - 1);
            E.cx--;
        } else{
            E.cx = E.row[E.cy - 1].size;
            editorRowAppendString(&E.row[E.cy - 1], row->chars, row->size);
            editorDeleteRow(E.cy);
            E.cy--;
        }
    }
    
    
    /* file i/o */
    
    char* editorRowsToString(int* bufferlength){
        int totalLength = 0;
        int j;
        for(j = 0; j < E.displayLength; j++){
            totalLength += E.row[j].size + 1;
        }
        *bufferlength = totalLength;
    
        char *buf = malloc(totalLength);
        char *p = buf;
        for(j = 0; j < E.displayLength; j++){
            memcpy(p, E.row[j].chars, E.row[j].size);
            p += E.row[j].size;
            *p = '\n';
            p++;
        }
        return buf;
    }
    
    void editorOpen(char* filename){
        // open a file given a file path
        free(E.fileName);
        E.fileName = strdup(filename);
    
        editorSelectSyntaxHighlight();
    
        FILE *filePath = fopen(filename, "r");
        if (!filePath){
            terminate("fopen");
        }
        char *line = NULL;
        size_t lineCap = 0;
        ssize_t lineLength;
        // read each line from this file into the row editorRow data struct chars feild
        while((lineLength = getline(&line, &lineCap, filePath)) != -1){
            // no need to read the carrige return and new line character
            while ((lineLength > 0) && ((line[lineLength - 1] == '\r') || 
                                        (line[lineLength - 1] == '\n')))
            {
                lineLength--;
                editorInsertRow(E.displayLength, line, lineLength);
            }
        }
        free(line);
        fclose(filePath);
        E.dirty = 0;
    }
    
    void editorSave(){
        if(E.fileName == NULL){
            E.fileName = editorPrompt("Save as (Esc to cancel): %s", NULL);
            if(E.fileName == NULL){
                editorSetStatusMessage("Save aborted");
                return;
            }
            editorSelectSyntaxHighlight();
        }
    
        int length;
        char *buffer = editorRowsToString(&length);
        int fd = open(E.fileName, O_RDWR | O_CREAT, 0644);
        if (fd != -1){
            if(ftruncate(fd, length) != -1){
                if(write(fd, buffer, length) == length){
                    close(fd);
                    free(buffer);
                    E.dirty = 0;
                    editorSetStatusMessage("%d bytes written to disk", length);
                    return;
                }
            }
            close(fd);
        }
        free(buffer);
        editorSetStatusMessage("Can't save! I/O error: %s", strerror(errno));
    }
    
    /* find */
    void editorFindCallback(char *query, int key){
        static int last_match = -1;
        static int direction = 1;
    
        static int saved_highLight_line;
        static char *saved_highLight = NULL;
    
        if(saved_highLight){
            memcpy(E.row[saved_highLight_line].highLight, saved_highLight, E.row[saved_highLight_line].renderSize);
            free(saved_highLight);
            saved_highLight = NULL;
        }
    
        if(key == '\r' || key == '\x1b'){
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
        for(i = 0; i < E.displayLength; i++){
            current += direction;
            if(current == -1){
                current = E.displayLength - 1;
            } else if(current == E.displayLength){
                current = 0;
            }
    
            EditorRow *row = &E.row[current];
            char *match = strstr(row->render, query);
            if(match){
                last_match = current;
                E.cy = current;
                E.cx = editorRowRxToCx(row, match - row->render);
                E.rowOffset = E.displayLength;
    
                saved_highLight_line = current;
                saved_highLight = malloc(row->size);
                memcpy(saved_highLight, row->highLight, row->renderSize);
                memset(&row->highLight[match - row->render], HL_MATCH, strlen(query));
                break;
            }
        }
    
    }
    
    void editorFind(){
        int saved_cx = E.cx;
        int saved_cy = E.cy;
        int saved_columnOffset = E.columnOffset;
        int saved_rowOffset = E.rowOffset;
    
        char* query = editorPrompt("Search: %s (ESC/Arrows/Enter): ", editorFindCallback);
        if(query){
            free(query);
        } else {
            E.cx = saved_cx;
            E.cy = saved_cy;
            E.columnOffset = saved_columnOffset;
            E.rowOffset = saved_rowOffset;
        }
    }
    void editorDrawRows(struct AppendBuffer *ab){
        // draw stuff
        int row;
        for(row = 0; row < E.screenRows; row++){
            int fileRow = row + E.rowOffset;
            if (fileRow >= E.displayLength){
                    // put welcome message 1/3 down the screen
                    if ((E.displayLength == 0) && (row == E.screenRows / 3)){
                        char welcome[80];
                        int welcomeLength = snprintf(welcome, sizeof(welcome),
                        "Texture Editor -- Version %s", TEXTURE_VERSION);
                        // if screen size is too small to fit the welcome message cut it off
                        if (welcomeLength > E.screenColumns){
                            welcomeLength = E.screenColumns;
                        }
                        // put the message in the middle of the screen
                        int padding = (E.screenColumns - welcomeLength) / 2;
                        if (padding){
                            abAppend(ab, "~", 1);
                            padding--;
                        }
                        while (padding--){
                            abAppend(ab, " ",  1);
                        }
                        abAppend(ab, welcome, welcomeLength);
                    } else{
                        abAppend(ab, "~", 1);
                    }
                } else {
                    // else write the val in the column
                    int length = E.row[fileRow].renderSize - E.columnOffset;
                    if (length < 0){
                        length = 0;
                    }
                    if (length > E.screenColumns){
                        length = E.screenColumns;
                    }
                    char *c = &E.row[fileRow].render[E.columnOffset];
                    unsigned char *highLight = &E.row[fileRow].highLight[E.columnOffset];
                    int current_color = -1;
                    int j;
                    for(j = 0; j < length; j++){
                        if(iscntrl(c[j])){
                            char sym = (c[j] <= 26) ? '@' + c[j] : '?';
                            abAppend(ab, "\x1b[7m", 4);
                            abAppend(ab, &sym, 1);
                            abAppend(ab, "\x1b[m", 3);
                            if(current_color != -1){
                                char buf[16];
                                int clen = snprintf(buf, sizeof(buf), "\x1b[%dm", current_color);
                                abAppend(ab, buf, clen);
                            }
                        } else if(highLight[j] == HL_NORMAL){
                            if(current_color != -1){
                                abAppend(ab, "\x1b[39m", 5);
                                current_color = -1;
                            }
                            abAppend(ab, &c[j], 1);
                        } else{
                            int color = editorSyntaxToColor(highLight[j]);
                            if(color != current_color){
                                current_color = color;
                                char buffer[16];
                            int clen = snprintf(buffer, sizeof(buffer), "\x1b[%dm", color);
                            abAppend(ab, buffer, clen);
                            }
                            abAppend(ab, &c[j], 1);
                        }
                    }
                    abAppend(ab, "\x1b[39m", 5);
                }
                // erase from cursor to end of line
                abAppend(ab, "\x1b[K", 3);
                // print to the next line
                abAppend(ab, "\r\n", 2);
        }
    }
}
