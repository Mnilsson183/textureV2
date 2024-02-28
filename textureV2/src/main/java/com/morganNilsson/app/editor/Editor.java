package com.morganNilsson.app.editor;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import com.morganNilsson.app.State;
import com.morganNilsson.app.terminal.TerminalMethods;

public class Editor {

    public static int cx;
    public static int cy;
    public static int rx;
    public static int rowOffset;
    public static int columnOffset;
    public static int displayLength;
    public static int dirty;
    public static EditorRow row = null;
    public static String fileName = null;
    public static char[] statusMessage[0] = '\u0000';
    public static statusMessage_time;
    public static syntax = null;

    public static void initEditor(){
        // cursor positions
        cx = 0;
        cy = 0;
        rx = 0;
        rowOffset = 0;
        columnOffset = 0;
        displayLength = 0;
        dirty = 0;
        statusMessage_time = 0;

        if (getWindowSize(&screenRows, &screenColumns) == -1){
            TerminalMethods.terminate("getWindowSize");
        }
        screenRows = screenRows - 2;
    }

    enum keys{
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

    enum highlight{
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

    public static int readKey(){
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

    public static int getCursorPosition(int[] rows, int[] columns){
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

    public static void updateSyntax(EditorRow row){
        row.highLight = realloc(row.highLight, row.renderSize);
        memset(row.highLight, HL_NORMAL, row.renderSize);
    
        if(syntax == NULL){
            return;
        }
    
        char[][] keywords = syntax.keywords;
    
        char[] singleLightCommentStart = syntax.singleline_comment_start;
        char[] multilineCommentStart = syntax.multiline_comment_start;
        char[] multilineCommentEnd = syntax.multiline_comment_end;
    
        int singleLightCommentStartLength = singleLightCommentStart ? strlen(singleLightCommentStart): 0;
        int multilineCommentStartLength = multilineCommentStart ? strlen(multilineCommentStart) : 0;
        int multilineCommentEndLength = multilineCommentEnd ? strlen(multilineCommentEnd) : 0;
    
    
        int prevSeparator = 1;
        int in_string = 0;
        int in_comment = 0;
    
        int i = 0;
        while (i < row.renderSize){
            char c = row.render[i];
            char prevHighlight = (i > 0) ? row.highLight[i - 1] : HL_NORMAL;
    
    
            if(singleLightCommentStartLength && !in_string){
                if(!strncmp(&row.render[i], singleLightCommentStart, singleLightCommentStartLength)){
                    memset(&row.highLight[i], HL_COMMENT, row.renderSize - i);
                    break;
                }
            }
    
            if(multilineCommentStartLength && multilineCommentEndLength && !in_string){
                if(in_comment){
                    row.highLight[i] = HL_MULTIPLE_LINE_COMMENT;
                    if(!strncmp(&row.render[i], multilineCommentStart, multilineCommentStartLength)){
                        memset(&row.highLight[i], HL_MULTIPLE_LINE_COMMENT, multilineCommentStartLength);
                        i += 2;
                        in_comment = 0;
                        prevSeparator = 1;
                        continue;
                    } else{
                        i++;
                        continue;
                    }
                } else if(!strncmp(&row.render[i], multilineCommentStart, multilineCommentStartLength)){
                        memset(&row.highLight[i], HL_MULTIPLE_LINE_COMMENT, multilineCommentStartLength);
                        i += multilineCommentStartLength;
                        in_comment = 1;
                        continue;
                }
            }
    
            if(syntax.flags & HL_HIGHLIGHT_STRINGS){
                if(in_string){
                    if(c == '\\' && i + 1 < row.renderSize){
                        row.highLight[i + 1] = HL_STRING;
                        i += 2;
                        continue;
                    }
                    row.highLight[i] = HL_STRING;
                    if(c == '\\' && i + 1 < row.renderSize){
                        row.highLight[i + 1] = HL_STRING;
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
                        row.highLight[i] = HL_STRING;
                        i++;
                        continue;
                    }
                }
            }
    
            if(syntax.flags & HL_HIGHLIGHT_NUMBERS){
                if((isdigit(c) && (prevSeparator || prevHighlight == HL_NUMBER)) || 
                (c =='.' && prevHighlight == HL_NUMBER)){
                    row.highLight[i] = HL_NUMBER;
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
    
                    if(!strncmp(&row.render[i], keywords[j], keywordLength) &&
                        isSeparator(row.render[i + keywordLength])){
                            memset(&row.highLight[i], keyword2 ? HL_KEYWORD2: HL_KEYWORD1, keywordLength);
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
    
    public static int syntaxToColor(int highLight){
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
    
    public static void selectSyntaxHighlight(){
        syntax = NULL;
        if(fileName == NULL){
            return;
        }
    
        char *extension = strrchr(fileName, '.');
    
        for(unsigned int j = 0; j < HighLightDataBase_ENTRIES; j++){
            struct EditorSyntax *s = &HighLightDataBase[j];
            unsigned int i = 0;
            while(s.fileMatch[i]){
                int is_extension = (s.fileMatch[i][0] == '0');
                if((is_extension && extension && !strcmp(extension, s.fileMatch[i])) ||
                    (!is_extension && strstr(fileName, s.fileMatch[i]))){
                        syntax = s;
    
                        int fileRow;
                        for(fileRow = 0; fileRow < displayLength; fileRow++){
                            editorUpdateSyntax(&row[fileRow]);
                        }
    
                        return;
                    }
                i++;
            }
        }
    }
    
    /* row operations */
    
    public static int rowCxToRx(EditorRow row, int cx){
        int rx = 0;
        int j;
        for(j = 0; j < cx; j++){
            if (row.chars[j] == '\t'){
                rx += (TEXTURE_TAB_STOP - 1) - (rx % TEXTURE_TAB_STOP);
            }
            rx++;
        }
        return rx;
    }
    
    public static int rowRxToCx(EditorRow row, int rx){
        int cur_rx = 0;
        int cx;
        for(cx = 0; cx < row.size; cx++){
            if (row.chars[cx] == '\t'){
                cur_rx += (TEXTURE_TAB_STOP - 1) - (cur_rx % TEXTURE_TAB_STOP);
            }
            cur_rx++;
            if(cur_rx > rx){
                return cx;
            }
        }
        return cx;
    }
    
    public static void updateRow(EditorRow row){
        int tabs = 0;
        int j;
        for (j = 0; j < row.size; j++){
            if (row.chars[j] == '\t'){
                tabs++;
            }
        }
        free(row.render);
        row.render = malloc(row.size + ( tabs * (TEXTURE_TAB_STOP - 1)) + 1);
    
        int tempLength = 0;
        for (j = 0; j < row.size; j++){
            if (row.chars[j] == '\t'){
                row.render[tempLength++] = ' ';
                while (tempLength % TEXTURE_TAB_STOP != 0){
                    row.render[tempLength++] = ' ';
                }
            } else{
                row.render[tempLength++] = row.chars[j];
            }
        }
        row.render[tempLength] = '\0';
        row.renderSize = tempLength;
    
        updateSyntax(row);
    }
    
    public static void insertRow(int at, String s, size_t length){
        if(at < 0 || at > displayLength){
            return;
        }
    
        row = realloc(row, sizeof(EditorRow) * (displayLength + 1));
        memmove(&row[at + 1], &row[at], sizeof(EditorRow) * (displayLength - at));
    
        // add a row to display
        row[at].size = length;
        row[at].chars = malloc(length + 1);
        memcpy(row[at].chars, s, length);
        row[at].chars[length] = '\0';
    
        row[at].renderSize = 0;
        row[at].render = NULL;
        row[at].highLight = NULL;
        editorUpdateRow(&row[at]);
    
        displayLength++;
        dirty++;
    }
    
    public static void freeRow(EditorRow row){
        free(row.render);
        free(row.chars);
        free(row.highLight);
    }
    
    public static void deleteRow(int at){
        if(at < 0 || at >= displayLength){
            return;
        }
        editorFreeRow(&row[at]);
        memmove(&row[at], &row[at + 1], sizeof(EditorRow) * (displayLength - at - 1));
        displayLength--;
        dirty++;
    }
    
    public static void rowInsertChar(EditorRow *row, int at, int c){
        if (at < 0 || at > row.size){ 
            at = row.size;
        }
        row.chars = realloc(row.chars, row.size + 2);
            memmove(&row.chars[at + 1], &row.chars[at], row.size - at + 1);
            row.size++;
            row.chars[at] = c;
            editorUpdateRow(row);
            dirty++;
    }
    
    public static void rowAppendString(EditorRow row, String s){
        row.chars = realloc(row.chars, row.size + s.length() + 1);
        memcpy(&row.chars[row.size], s, s.length());
        row.size += s.length();
        row.chars[row.size] = '\0';
        editorUpdateRow(row);
        dirty++;
    }
    
    public static void rowDeleteChar(EditorRow row, int at){
        if (at < 0 || at >= row.size){
            return;
        }
        memmove(&row.chars[at], &row.chars[at + 1], row.size - at);
        row.size--;
        editorUpdateRow(row);
        dirty++;
    }
    
    /* Editor Functions */
    public static void insertChar(int c){
        if (cy == displayLength){
            editorInsertRow(displayLength, "", 0);
        }
        editorRowInsertChar(&row[cy], cx, c);
        cx++;
    }
    
    public static void insertNewLine(){
        if(cx == 0){
            editorInsertRow(cy, "", 0);
        } else{
            EditorRow row = &row[cy];
            editorInsertRow(cy + 1, &row.chars[cx], row.size - cx);
            row = &row[cy];
            row.size = cx;
            row.chars[row.size] = '\0';
            editorUpdateRow(row);
        }
        cy++;
        cx = 0;
    }
    
    
    public static void deleteChar(){
        if(cy == displayLength){
            return;
        }
        if(cx == 0 && cy == 0){
            return;
        }
    
        EditorRow row = &row[cy];
    
        if(cx > 0){
            editorRowDeleteChar(row, cx - 1);
            cx--;
        } else{
            cx = row[cy - 1].size;
            editorRowAppendString(&row[cy - 1], row.chars, row.size);
            editorDeleteRow(cy);
            cy--;
        }
    }
    
    
    /* file i/o */
    
    public static String rowsToString(int[] bufferlength){
        int totalLength = 0;
        int j;
        for(j = 0; j < displayLength; j++){
            totalLength += row[j].size + 1;
        }
        *bufferlength = totalLength;
    
        char *buf = malloc(totalLength);
        char *p = buf;
        for(j = 0; j < displayLength; j++){
            memcpy(p, row[j].chars, row[j].size);
            p += row[j].size;
            *p = '\n';
            p++;
        }
        return buf;
    }
    
    public static void open(String filename){
        // open a file given a file path
        free(fileName);
        fileName = strdup(filename);
    
        editorSelectSyntaxHighlight();
    
        FILE *filePath = fopen(filename, "r");
        if (!filePath){
            terminate("fopen");
        }
        String line = NULL;
        size_t lineCap = 0;
        int lineLength = line.length();
        // read each line from this file into the row editorRow data struct chars feild
        while((lineLength = getline(&line, &lineCap, filePath)) != -1){
            // no need to read the carrige return and new line character
            while ((lineLength > 0) && ((line[lineLength - 1] == '\r') || 
                                        (line[lineLength - 1] == '\n')))
            {
                lineLength--;
                editorInsertRow(displayLength, line, lineLength);
            }
        }
        free(line);
        fclose(filePath);
        dirty = 0;
    }
    
    public static void save(){
        if(fileName == NULL){
            fileName = editorPrompt("Save as (Esc to cancel): %s", NULL);
            if(fileName == NULL){
                editorSetStatusMessage("Save aborted");
                return;
            }
            editorSelectSyntaxHighlight();
        }
    
        int length;
        String buffer = editorRowsToString(&length);
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
        editorSetStatusMessage("Can't save! I/O error");
    }
    
    /* find */
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
    
            EditorRow row = &row[current];
            String match = strstr(row.render, query);
            if(match){
                last_match = current;
                cy = current;
                cx = editorRowRxToCx(row, match - row.render);
                rowOffset = displayLength;
    
                saved_highLight_line = current;
                saved_highLight = malloc(row.size);
                memcpy(saved_highLight, row.highLight, row.renderSize);
                memset(&row.highLight[match - row.render], HL_MATCH, strlen(query));
                break;
            }
        }
    
    }
    
    public static void find(){
        int saved_cx = cx;
        int saved_cy = cy;
        int saved_columnOffset = columnOffset;
        int saved_rowOffset = rowOffset;
    
        String query = editorPrompt("Search: %s (ESC/Arrows/Enter): ", editorFindCallback);
        if(query){
            free(query);
        } else {
            cx = saved_cx;
            cy = saved_cy;
            columnOffset = saved_columnOffset;
            rowOffset = saved_rowOffset;
        }
    }
    public static void drawRows(struct AppendBuffer *ab){
        // draw stuff
        int row;
        for(row = 0; row < screenRows; row++){
            int fileRow = row + rowOffset;
            if (fileRow >= displayLength){
                    // put welcome message 1/3 down the screen
                    if ((displayLength == 0) && (row == screenRows / 3)){
                        char[] welcome = new char[80];
                        int welcomeLength = snprintf(welcome, sizeof(welcome),
                        "Texture Editor -- Version %s", TEXTURE_VERSION);
                        // if screen size is too small to fit the welcome message cut it off
                        if (welcomeLength > screenColumns){
                            welcomeLength = screenColumns;
                        }
                        // put the message in the middle of the screen
                        int padding = (screenColumns - welcomeLength) / 2;
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
                    int length = row[fileRow].renderSize - columnOffset;
                    if (length < 0){
                        length = 0;
                    }
                    if (length > screenColumns){
                        length = screenColumns;
                    }
                    String c = &row[fileRow].render[columnOffset];
                    unsigned char *highLight = &row[fileRow].highLight[columnOffset];
                    int current_color = -1;
                    int j;
                    for(j = 0; j < length; j++){
                        if(iscntrl(c[j])){
                            char sym = (c[j] <= 26) ? '@' + c[j] : '?';
                            ab.append(ab, "\x1b[7m");
                            ab.append(ab, &sym);
                            ab.append(ab, "\x1b[m");
                            if(current_color != -1){
                                char[] buf = new char[16];
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
    public static void setStatusMessage(String fmt){
        va_list ap;
        va_start (ap, fmt);
        vsnprintf(E.statusMessage, sizeof(E.statusMessage), fmt, ap);
        va_end(ap);
        E.statusMessage_time = time(NULL);
    }

    public static void refreshScreen(){
        editorScroll();
    
        AppendBuffer ab = APPEND_INIT;
    
        // hide the cursor
        abAppend(&ab, "\x1b[?25l", 6);
        // move the cursor to the 1,1 position in the terminal
        abAppend(&ab, "\x1b[H", 3);
    
        editorDrawRows(&ab);
        editorDrawStatusBar(&ab);
        editorDrawMessageBar(&ab);
    
        char buf[32];
        snprintf(buf, sizeof(buf), "\x1b[%d;%dH",   (E.cy - E.rowOffset) + 1, 
                                                    (E.rx - E.columnOffset) + 1);
        abAppend(&ab, buf, strlen(buf));
    
        // show cursor again
        abAppend(&ab, "\x1b[?25h", 6);
    
        write(STDOUT_FILENO, ab.b, ab.len);
        abFree(&ab);
    }

    void processKeyPress(){
        static int quit_times = TEXTURE_QUIT_TIMES;
    
        int c = editorReadKey();
    
        switch (c){
            case '\r':
                editorInsertNewLine();
                break;
    
            // exit case
            case CTRL_KEY('q'):
                if(E.dirty && quit_times > 0){
                    editorSetStatusMessage("WARNING!! file has unsaved changes. "
                    "Press Ctrl-Q %d more times to quit", quit_times);
                    quit_times--;
                    return;
                }
                // write the 4 byte erase in display to the screen
                write(STDOUT_FILENO, "\x1b[2J", 4);
                // move the cursor to the 1,1 position in the terminal
                write(STDOUT_FILENO, "\x1b[H", 3);
                exit(0);
                break;
    
            case CTRL_KEY('s'):
                editorSave();
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