// basic c text editor

// step 177

/** INCLUDES **/
#define _DEFAULT_SOURCE
#define _BSD_SOURCE
#define _GNU_source

#include <string.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/types.h>
#include <termios.h>
#include <unistd.h>
#include <time.h>
#include <stdarg.h>

/** DEFINES**/
#define CTRL_KEY(key) ((key) & 0x1f)

#define true 1
#define false 0

#define APPEND_INIT {NULL, 0}

/* editor options */
#define TEXTURE_VERSION "0.01"
#define TEXTURE_TAB_STOP 8
#define TEXTURE_QUIT_TIMES 3

#define HL_HIGHLIGHT_NUMBERS (1<<0)
#define HL_HIGHLIGHT_STRINGS (1<<1)

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
/* prototypes */
void editorSetStatusMessage(const char *fmt, ...);
void editorRefreshScreen(void);
char *editorPrompt(char *prompt, void (*callback)(char *, int));


/** DATA **/
typedef struct EditorRow{
    int size;
    int renderSize;
    char* chars;
    char* render;
    unsigned char *highLight;
} EditorRow;

struct AppendBuffer{
    // buffer to minimize write to terminal functions
    char *b;
    int len;
};

struct EditorSyntax{
    char *filetype;
    char **fileMatch;
    char **keywords;
    char *singleline_comment_start;
    char *multiline_comment_start;
    char *multiline_comment_end;
    int flags;
};


// global var that is the default settings of terminal
struct EditorConfig{
    // cursor position
    int cx, cy;
    int rx;
    // screen offsets for moving cursor off screen
    int rowOffset;
    int columnOffset;
    // default terminal settings
    struct termios orig_termios;
    int dirty;
    // rows and columns of the terminal
    int screenRows, screenColumns;
    int displayLength;
    EditorRow* row;
    char* fileName;
    struct EditorSyntax *syntax;
    char statusMessage[80];
    time_t statusMessage_time;
};

struct EditorConfig E;

/* filetypes */
char *C_HL_extensions[] = {".c", ".h", ".cpp", NULL};
char *C_HL_keywords[] = {
    "switch", "if", "while", "for", "break", "continue", "return", "else",
    "struct", "union", "typedef", "static", "enum", "class", "case",
    "int|", "long|", "double|", "float|", "char|", "unsigned|", "signed|",
    "void|", NULL
};

struct EditorSyntax HighLightDataBase[] = {
    {
    "c",
    C_HL_extensions,
    C_HL_keywords,
    "//", "/*", "*/",
    HL_HIGHLIGHT_NUMBERS | HL_HIGHLIGHT_STRINGS
    },
};

#define HighLightDataBase_ENTRIES (sizeof(HighLightDataBase) / sizeof(HighLightDataBase[0]))

/** TERMINAL**/
void terminate(const char *s){
    // write the 4 byte erase in display to the screen
    write(STDOUT_FILENO, "\x1b[2J", 4);
    // move the cursor to the 1,1 position in the terminal
    write(STDOUT_FILENO, "\x1b[H", 3);
    // function to deal with the error outputs
    perror(s);
    exit(1);
}

void disableRawMode(void){
    // set the terminal attributes to the original values
    if(tcsetattr(STDIN_FILENO, TCSAFLUSH, &E.orig_termios) == -1){
        terminate("tcsetattr");
    }
}
void enableRawMode(void){
    // function to enter raw mode of the terminal

    // tcgetattr reads the terminal attributes
    if(tcgetattr(STDIN_FILENO, &E.orig_termios) == -1){
        terminate("tcgetattr");
    }
    // atexit disable the raw mode once the program finishes running
    atexit(disableRawMode);

    struct termios raw = E.orig_termios;

    // terminal flags and other specifiers that allow out program to output
    raw.c_iflag &= ~(BRKINT | ICRNL | INPCK | ISTRIP | IXON);
    raw.c_oflag &= ~(OPOST);
    raw.c_lflag &= ~(ECHO | ICANON | IEXTEN | ISIG);
    
    // set the parameters of the read function
    // VMIN is the number of characters read before stopping reading
    raw.c_cc[VMIN] = 0;
    // VTIME sets the timeout of the terminal read function
    raw.c_cc[VTIME] = 1;

    // Set the terminals attributes to reflect raw mode
    // TCSAFLUSH waits for pending output to be printed to screen
    if(tcsetattr(STDIN_FILENO, TCSAFLUSH, &raw) == -1){
        terminate("tcsetattr");
    }
}
int editorReadKey() {
    int nread;
    char c;
    // read each key press
    while ((nread = read(STDIN_FILENO, &c, 1)) != 1) {
        if (nread == -1 && errno != EAGAIN){
            terminate("read");
        }
    }
    // first char is the first char of ASCII escape code
    if (c == '\x1b'){
        // sequence of 3 chars first being the ASCII escape code
        char seq[3];

        // read the STDIN and set both the first and second characters in seq
        if (read(STDIN_FILENO, &seq[0], 1) != 1){
            return '\x1b';
        }
        if (read(STDIN_FILENO, &seq[1], 1) != 1){
            return '\x1b';
        }
        
        // first char being the special char
        if(seq[0] == '['){
            // check that that seq falls into the bounds of our answer
            if (seq[1] >= '0' && seq[1] <= '9'){
                // read the STDIN to seq
                if (read(STDIN_FILENO, &seq[2], 1) != 1){
                    return '\x1b';
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
        return '\x1b';
    } else {
        // if no issue each key press
        return c;
    }
}

int getCursorPosition(int* rows, int* columns){
    // man read to get the cursor position
    char buf[32];
    unsigned int i = 0;
    // write a cursor position report
    if (write(STDOUT_FILENO, "\x1b[6n", 4) != 4){
        return -1;
    }

    // 
    while (i < sizeof(buf) - 1){
        if (read(STDIN_FILENO, &buf[i], 1)){
            break;
        }
        if(buf[i] == 'R'){
            break;
        }
        i++;
    }
    buf[i] = '\0';
    if (buf[0] != '\x1b' || buf[1] != '['){
        return -1;
    }
    if (sscanf(&buf[2],"%d;%d", rows, columns) != 2){
        return -1;
    }
    return 0;
    
}

int getWindowSize(int* rows, int* columns){
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
/* Syntax highlighting */
int isSeparator(int c){
    return isspace(c) || c == '\0' || strchr(",.()+-/*=~%<>[];", c);
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

/* APPEND BUFFER */

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

/** INPUT**/
char *editorPrompt(char *prompt, void (*callback)(char *, int)){
    size_t bufferSize = 128;
    char *buffer = malloc(bufferSize);

    size_t bufferLength = 0;
    buffer[0] = '\0';

    while (true){
        editorSetStatusMessage(prompt, buffer);
        editorRefreshScreen();

        int c = editorReadKey();
        if(c == DEL_KEY || c == CTRL_KEY('h') || c == BACKSPACE){
            if(bufferLength != 0){
                buffer[--bufferLength] = '\0';
            }
        }else if(c == '\x1b'){
            editorSetStatusMessage("");
            if(callback){
                callback(buffer, c);
            }
            free(buffer);
            return NULL;
        } else if(c == '\r'){
            if(bufferLength != 0){
                editorSetStatusMessage("");
                if(callback){
                    callback(buffer, c);
                }
                return buffer;
            }
        } else if(!iscntrl(c) && c < 128){
            if(bufferLength == bufferSize - 1){
                bufferSize *= 2;
                buffer = realloc(buffer, bufferSize);
            }
            buffer[bufferLength++] = c;
            buffer[bufferLength] = '\0';
        }

        if(callback){
            callback(buffer, c);
        }
    }
}

void editorMoveCursor(int key){
    EditorRow* row = (E.cy >= E.displayLength) ? NULL: &E.row[E.cy];
    
    // update the cursor position based on the key inputs
    switch (key)
    {
        case ARROW_LEFT:
            if (E.cx != 0){
                E.cx--;
            } else if(E.cy > 0){
                E.cy--;
                E.cx = E.row[E.cy].size;
            }
            break;
        case ARROW_RIGHT:
            if (row && E.cx < row->size){
                E.cx++;
            // if go right on a the end of line
            } else if(row && E.cx == row->size){
                E.cy++;
                E.cx = 0;
            }
            break;
        case ARROW_UP:
            if (E.cy != 0){
                E.cy--;
            }
            break;
        case ARROW_DOWN:
            if (E.cy < E.displayLength){
                E.cy++;
            }
            break;
    }

    // snap cursor to the end of line
    row = (E.cy >= E.displayLength) ? NULL : &E.row[E.cy];
    int rowLength = row ? row->size : 0;
    if (E.cx > rowLength){
        E.cx = rowLength;
    }
}

void editorProcessKeyPress(void){
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
        case '\x1b':
            break;

        default:
            editorInsertChar(c);
            break;
    }
    quit_times = TEXTURE_QUIT_TIMES;
}

/** OUTPUT **/
void editorScroll(){
    // moving the screen around the file
    if (E.cy < E.displayLength){
        E.rx = editorRowCxToRx(&E.row[E.cy], E.cx);
    }

    if (E.cy < E.rowOffset){
        E.rowOffset = E.cy;
    }
    if (E.cy >= E.rowOffset + E.screenRows){
        E.rowOffset = E.cy - E.screenRows + 1;
    }
    if (E.rx < E.columnOffset){
        E.columnOffset = E.rx;
    }
    if (E.rx >= E.columnOffset + E.screenColumns){
        E.columnOffset = E.rx - E.screenColumns + 1;
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

void editorDrawStatusBar(struct AppendBuffer *ab){
    abAppend(ab, "\x1b[7m", 4);
    char status[80], rStatus[80];
    int length = snprintf(status, sizeof(status), "%.20s - %d lines %s", 
        E.fileName ? E.fileName : "[No Name]", E.displayLength,
        E.dirty ? "(modified)": "");
    int rlen = snprintf(rStatus, sizeof(rStatus), "%s | %d%d",
        E.syntax ? E.syntax->filetype : "No Filetype", E.cy + 1, E.displayLength);
    if(length > E.screenColumns){
        length = E.screenColumns;
    }
    abAppend(ab , status, length);
    while(length < E.screenColumns){
        if (E.screenColumns - length == rlen){
            abAppend(ab, rStatus, rlen);
            break;
        } else{
            abAppend(ab, " ", 1);
            length++;
        }
    }
    abAppend(ab, "\x1b[m", 3);
    abAppend(ab, "\r\n", 2);
}

void editorDrawMessageBar(struct AppendBuffer *ab){
    abAppend(ab, "\x1b[K", 3);
    int messageLength = strlen(E.statusMessage);
    if (messageLength > E.screenColumns){
        messageLength = E.screenColumns;
    }
    if (messageLength && time(NULL) - E.statusMessage_time < 5){
        abAppend(ab, E.statusMessage, messageLength);
    }
}

void editorRefreshScreen(void){
    editorScroll();

    struct AppendBuffer ab = APPEND_INIT;

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

void editorSetStatusMessage(const char *fmt, ...){
    va_list ap;
    va_start (ap, fmt);
    vsnprintf(E.statusMessage, sizeof(E.statusMessage), fmt, ap);
    va_end(ap);
    E.statusMessage_time = time(NULL);
}

/** INIT **/
void initEditor(void){
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

int main(int argc, char* argv[]){
    enableRawMode();
    initEditor();
    // check the passed number of args
    if (argc >= 2){
        editorOpen(argv[1]);
    }

    editorSetStatusMessage("HELP: Ctrl-q to quit | Ctrl-s to save | Ctrl-f find");
    
    while (true){
        editorRefreshScreen();
        editorProcessKeyPress();
    }
    return 0;
}
