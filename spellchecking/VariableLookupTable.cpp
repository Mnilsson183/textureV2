#include <iostream>

class VariableLookupTable{

private:
    std::map<std::string, bool> map;
public:
    ~VariableLookupTable();
    void VariableLookupTable::addType(std::string s);
    void VariableLookupTable::removeType(std::string s)
    bool VariableLookupTable::exists(std::string s)
};

VariableLookupTable::~TypeLookupTable(){
    this.map = new std::map<std::string, bool>;
}

void VariableLookupTable::addType(std::string s){
    this.map.add({s, true});
}

void VariableLookupTable::removeType(std::string s){
    this.map.remove(s);
}

bool VariableLookupTable::exists(std::string s){
    if(this.map.get(s) == NULL) return false;
    else return true;
}

