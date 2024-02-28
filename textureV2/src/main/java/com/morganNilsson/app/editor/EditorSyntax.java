package com.morganNilsson.app.editor;

public class EditorSyntax {
    char[] filetype;
    char[][] fileMatch;
    char[][] keywords;
    char[] singleline_comment_start;
    char[] multiline_comment_start;
    char[] multiline_comment_end;
    int flags;
    public void editorUpdateSyntax(EditorRow[] row){
        row.highLight = realloc(row.highLight, row.renderSize);
        memset(row.highLight, HL_NORMAL, row.renderSize);
    
        if(E.syntax == null){
            return;
        }
    
        char[][] keywords = E.syntax.keywords;
    
        char[] singleLightCommentStart = E.syntax.singleline_comment_start;
        char[] multilineCommentStart = E.syntax.multiline_comment_start;
        char[] multilineCommentEnd = E.syntax.multiline_comment_end;
    
        int singleLightCommentStartLength = singleLightCommentStart != 0 ? singleLightCommentStart.length: 0;
        int multilineCommentStartLength = multilineCommentStart != 0 ? multilineCommentStart.length : 0;
        int multilineCommentEndLength = multilineCommentEnd != 0 ? multilineCommentEnd.length : 0;
    
    
        int prevSeparator = 1;
        int in_string = 0;
        int in_comment = 0;
    
        int i = 0;
        while (i < row.renderSize){
            char c = row.render[i];
            unsigned char prevHighlight = (i > 0) ? row.highLight[i - 1] : HL_NORMAL;
    
    
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
    
            if(E.syntax.flags & HL_HIGHLIGHT_STRINGS){
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
    
            if(E.syntax.flags & HL_HIGHLIGHT_NUMBERS){
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

}
