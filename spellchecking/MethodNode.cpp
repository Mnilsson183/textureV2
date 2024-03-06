#include <iostream>
#include <sstream>

class MethodNode
{
private:
    const std::string functionName;
    std::string parseMethodLine(std::string s);
public:
    MethodNode(std::string functionLine);
    ~MethodNode();
};

MethodNode::MethodNode(std::string functionLine){
    std::string parsedFunction = parseMethodLine(functionLine);
}
// returns (return type) (method name) n * ((parameter name) (parameter type))
// TODO add error checking
// TODO deal with duplicates from prototypes
std::string MethodNode::parseMethodLine(std::string functionHeading){
    std::string s;
    int i = 0;
    // read the return type
    std::string word;
    while(functionHeading[i] != ' '){
        word += functionHeading[i];
        i++;
    }
    i++;
    s = s + word + ' ';
    word = "";
    while(functionHeading[i] != '('){
        word += functionHeading[i];
        i++;
    }
    i++;
    s = s + word + ' ';
    word = "";
    while(functionHeading[i] != ')'){
        while(functionHeading[i] != ' ' || functionHeading[i] != ')'){
            word += functionHeading[i];
            i++;
        }
        i++;
        s = s + word + ' ';
        word = "";
    }
}

MethodNode::~MethodNode(){

}
