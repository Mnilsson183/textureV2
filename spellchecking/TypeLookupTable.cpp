#include <iostream>

class TypeLookupTable{

private:
    std::map<std::string, bool> map;
public:
    ~TypeLookupTable();
    void TypeLookupTable::addType(std::string s);
    void TypeLookupTable::removeType(std::string s);
    bool TypeLookUpTable::exists(std::string s);
};

TypeLookupTable::~TypeLookupTable(){
    this.map = new std::map<std::string, bool>;
}

void TypeLookupTable::addType(std::string s){
    this.map.add({s, true});
}

void TypeLookupTable::removeType(std::string s){
    this.map.remove(s);
}

bool TypeLookUpTable::exists(std::string s){
    if(this.map.get(s) == NULL) return false;
    else return true;
}

