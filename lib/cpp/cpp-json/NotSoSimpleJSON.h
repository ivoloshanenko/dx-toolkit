#ifndef __NOT_SO_SIMPLE_JSON_H__
#define __NOT_SO_SIMPLE_JSON_H__

#include <cstdio>
#include <cstring>
#include <vector>
#include <map>
#include <cstdlib>
#include <string>
#include <iostream>
#include <cstdio>
#include <exception>
#include <cassert>
#include <sstream>
#include <istream>
#include <ostream>
#include <limits>
#include <typeinfo>
#include <cmath>
#include <algorithm>

#include "utf8/source/utf8.h"

typedef long long int64;

class JSONException {
public:
  std::string err;
  JSONException(const std::string &e): err(e) {}
};

/*
// Base class for all JSON related runtime errors 
class JSONException: public std::exception {
public:
  std::string err;
  JSONException(): err("An Unknown error occured");
  JSONException(const std::string &e): err(e) {}
  virtual const char* what() const throw() {
    return (const char*)err.c_str();
  }
  virtual ~JSONException() throw() { }
};*/
/////////////////////////////////////////////////

// TODO: - Support strict flag for utf-8 enforcement (both object "keys", and json String values)
//       - Write tests
//       - Run tests using STL algorithms for JSON
//       - Error when trying to do j8["key"] = j8 for a JSON_OBJECT j8 : Expected, but better if fixed
//       - Document the code

enum json_values {
  JSON_UNDEFINED = 0,
  JSON_OBJECT = 1,
  JSON_HASH = 1, // JSON_HASH and JSON_OBJECT are aliases (both have value = 1)
  JSON_ARRAY = 2,
  JSON_INTEGER = 3,
  JSON_REAL = 4,
  JSON_STRING = 5,
  JSON_BOOLEAN = 6,
  JSON_NULL = 7
};

// Each class can have a function which returns a JSON*, which basically says, read my self and return a  JSON object ?? 

class Value {
public:
//  virtual const std::string toString() const = 0; // Everybody should implement this function
  virtual const json_values type() const = 0; // Return type of particular dervied class
//  virtual Value* readFromStream(std::ostream& ) = 0; // Read value of whatever type from stream and return *this
  virtual void write(std::ostream &out) const = 0;
  virtual Value* returnMyNewCopy() const = 0;
  virtual void read(std::istream &in) = 0;
  virtual bool isEqual(const Value* other) const = 0;
  /*template <typename T>
  operator T*() {
    std::cout<<"Dynamic castiiiiiiiiiing "<<std::string(typeid(T).name())<<std::endl;
    T *p = dynamic_cast<T*>(this);
    if (p == NULL)
      throw JSONException("Illegal conversion from Value* to " + std::string(typeid(T).name()));
    return p;
  }*/

//  friend ostream& operator <<(std::ostream&, const Value&);
//  friend istream& operator >>(istream&, Value &);
};

// Forward declarations
class Integer;
class Real;
class Null;
class Boolean;
class String;
class Array;
class Object;

// Class for top level json
class JSON {
public:

  typedef std::map<std::string, JSON>::iterator object_iterator;
  typedef std::map<std::string, JSON>::const_iterator const_object_iterator;
  typedef std::vector<JSON>::iterator array_iterator;
  typedef std::vector<JSON>::const_iterator const_array_iterator;

  typedef std::map<std::string, JSON>::const_reverse_iterator object_reverse_iterator;
  typedef std::map<std::string, JSON>::reverse_iterator const_object_reverse_iterator;
  typedef std::vector<JSON>::reverse_iterator array_reverse_iterator;
  typedef std::vector<JSON>::const_reverse_iterator const_array_reverse_iterator;


  // Will be exactly one of these 2
  Value *val; // Default: NULL

  static double epsilon; // initilized a top-level in NotSoSimpleJSON.cpp (default = 1e-12)
  static void setEpsilon(double v) { epsilon = v; }
  static double getEpsilon() { return epsilon;}
  static JSON parse(const std::string &str) {
    JSON tmp;
    tmp.ReadFromString(str);
    return tmp;
  }
  // This will be a simple function call now
  //enum json_type type; // Default value if JSON_UNDEFINED
  
  JSON():val(NULL) {}

  JSON(const JSON &rhs);
  JSON(const json_values &rhs);

  template<typename T>
  JSON(const T& x);

  void clear() { delete val; val=NULL; }

  void write(std::ostream &out) const;
  void read(std::istream &in);
  
  void ReadFromString(const std::string&); // Populate JSON from a string
  std::string ToString(bool onlyTopLevel = false) const;

  bool operator ==(const JSON& other) const;
  bool operator !=(const JSON& other) const { return !(*this == other); }

  const JSON& operator [](const size_t &indx) const;
  const JSON& operator [](const std::string &s) const;
  const JSON& operator [](const JSON &j) const;
  const JSON& operator [](const char *str) const;
  template<typename T>
  const JSON& operator [](const T&x) const;

  JSON& operator [](const size_t &indx);
  JSON& operator [](const std::string &s);
  JSON& operator [](const JSON &j);
  JSON& operator [](const char *str);
  template<typename T>
  JSON& operator [](const T& x) { return const_cast<JSON&>( (*(const_cast<const JSON*>(this)))[x]); }

  // A non-templatized specialization is always preferred over template version
  template<typename T> JSON& operator =(const T &rhs);
  JSON& operator =(const JSON &);
  JSON& operator =(const char &c);
  JSON& operator =(const std::string &s);
  JSON& operator =(const bool &x);
  JSON& operator =(const char s[]);
  JSON& operator =(const Null &x);

  template<typename T>
  JSON& operator =(const std::vector<T> &vec) {
    clear();
    this->val = new Array(vec);
    return *this;
  }
  template<typename T>
  JSON& operator =(const std::map<std::string, T> &m) {
    clear();
    this->val = new Object(m);
    return *this;
  }

  template<typename T>
  operator T() const;
  
  const json_values type() const { return (val == NULL) ? JSON_UNDEFINED : val->type(); }

  size_t size() const;
  size_t length() const { return size(); }
  
  template<typename T>
  bool has(const T &indx) const { return has(static_cast<size_t>(indx)); }
  bool has(const size_t &indx) const;
  bool has(const std::string &key) const;
  bool has(const JSON &j) const;
  bool has(const char *key) const;

  void push_back(const JSON &j);
  void erase(const size_t &indx);
  void erase(const std::string &key);
 
  // Forward Iterators
  const_object_iterator object_begin() const;
  object_iterator object_begin();
  const_array_iterator array_begin() const;
  array_iterator array_begin();
 
  const_object_iterator object_end() const;
  object_iterator object_end();
  const_array_iterator array_end() const;
  array_iterator array_end();

  // Reverse Iterators
  const_object_reverse_iterator object_rbegin() const;
  object_reverse_iterator object_rbegin();
  const_array_reverse_iterator array_rbegin() const;
  array_reverse_iterator array_rbegin();
 
  const_object_reverse_iterator object_rend() const;
  object_reverse_iterator object_rend();
  const_array_reverse_iterator array_rend() const;
  array_reverse_iterator array_rend();


  ~JSON() { clear(); } 
  // TODO: Handle case of streams
};


/*ostream& operator >>(std::ostream& o, const Value& v) {
  v.write(o);
  return o;
}*/

// Implicit copy constructor will suffice for all of the classes below

class Integer: public Value {
public:
  int64 val;

  Integer() {}
  Integer(const int64 &v):val(v) {}
  void write(std::ostream &out) const { out<<val; }
  const json_values type() const { return JSON_INTEGER; }
  size_t returnAsArrayIndex() const { return static_cast<size_t>(val);}
  Value* returnMyNewCopy() const { return new Integer(*this); }
  operator const Value* () { return this; }
  bool isEqual(const Value *other) const;
  bool operator ==(const Integer& other) const { return isEqual(&other); }
  bool operator !=(const Integer &other) const { return !(*this == other); }

  // read() Should not be called for Integer and Real, 
  // use ReadNumberValue() instead for these two "special" classes
  void read(std::istream &in) { assert(false); }
};

class Real: public Value {
public:
  double val;

  Real() {}
  Real(const double &v):val(v) {}
  void write(std::ostream &out) const { out<<val; }
  const json_values type() const { return JSON_REAL; }
  size_t returnAsArrayIndex() const { return static_cast<size_t>(val);}
  Value* returnMyNewCopy() const { return new Real(*this); }
  bool isEqual(const Value *other) const;
  bool operator ==(const Real& other) const { return isEqual(&other); }
  bool operator !=(const Real& other) const { return !(*this == other); }
 
  // read() Should not be called for Integer and Real, 
  // use ReadNumberValue() instead for these two "special" classes
  void read(std::istream &in) { assert(false); }
};

class String: public Value {
public:
  std::string val;
  
  String() {}
  String(const std::string &v):val(v) {}
  // TODO: Make sure cout<<stl::string workes as expected;
  void write(std::ostream &out) const;
  const json_values type() const { return JSON_STRING; }
  std::string returnString() const { return val; }
  Value* returnMyNewCopy() const { return new String(*this); }
  void read(std::istream &in);
  bool isEqual(const Value *other) const;
  bool operator ==(const String& other) const { return isEqual(&other); }
  bool operator !=(const String& other) const { return !(*this == other); }
 
  // Should have a constructor which allows creation from std::string directly.
};

class Object: public Value {
public:
  std::map<std::string, JSON> val;
  
  Object() { }
  Object(const Object &rhs): val(rhs.val) {}
  
  template<typename T> 
  Object(const std::map<std::string, T> &v) {
    val.insert(v.begin(), v.end());
  }

  JSON& jsonAtKey(const std::string &s);
  const JSON& jsonAtKey(const std::string &s) const;
  void write(std::ostream &out) const;
  const json_values type() const { return JSON_OBJECT; }
  Value* returnMyNewCopy() const { return new Object(*this); }
  void read(std::istream &in);
  void erase(const std::string &key);
  bool isEqual(const Value *other) const;
  bool operator ==(const Object& other) const { return isEqual(&other); }
  bool operator !=(const Object& other) const { return !(*this == other); }
};

class Array: public Value {
public:
  std::vector<JSON> val;
  
  Array() { }
  Array(const Array& arr): val(arr.val) {}

  template<typename T>
  Array(const std::vector<T> &vec) {
    for (unsigned i = 0;i < vec.size(); i++)
      val.push_back(*(new JSON(vec[i])));
  }

  JSON& jsonAtIndex(size_t i);
  const JSON& jsonAtIndex(size_t i) const;
  void write(std::ostream &out) const;
  const json_values type() const { return JSON_ARRAY; }
  Value* returnMyNewCopy() const { return new Array(*this); }
  void read(std::istream &in);
  void push_back(const JSON &j) {
    val.push_back(j);
  }
  void erase(const size_t &i);
  bool isEqual(const Value* other) const;
  bool operator ==(const Array& other) const { return isEqual(&other); }
  bool operator !=(const Array& other) const { return !(*this == other); }
 

};

class Boolean: public Value {
public:
  bool val;
  
  Boolean() {}
  Boolean(const bool &v):val(v) {}
  JSON& jsonAtKey(const std::string &s);
  const JSON& jsonAtKey(const std::string &s) const;
  const json_values type() const { return JSON_BOOLEAN; }
  void write(std::ostream &out) const { out<<((val) ? "true" : "false"); }
  Value* returnMyNewCopy() const { return new Boolean(*this); }
  void read(std::istream &in);
  bool isEqual(const Value* other) const;
  bool operator ==(const Boolean& other) const { return isEqual(&other); }
  bool operator !=(const Boolean& other) const { return !(*this == other); } 
};

class Null: public Value {
public:
  void write(std::ostream &out) const { out<<"null"; }
  const json_values type() const { return JSON_NULL; }
  Value* returnMyNewCopy() const { return new Null(*this); }
  void read(std::istream &in);
  bool isEqual(const Value* other) const;
  bool operator ==(const Null& other) const { return isEqual(&other); }
  bool operator !=(const Null& other) const { return !(*this == other); } 

};

template<typename T>
JSON::JSON(const T& x) {
  val = NULL; // So that clear() works fine on this, else we will be deallocating some arbitrary memory - dangerous!
  *this = operator=(x);
}

template<typename T>
JSON& JSON::operator =(const T &x) {
  if (!std::numeric_limits<T>::is_specialized)
    throw JSONException("Sorry! We do not allow creating a JSON object from " + std::string(typeid(x).name()) + " type.");
  
  clear();
  if(std::numeric_limits<T>::is_integer)
    this->val = new Integer(static_cast<int64>(x));
  else
    this->val = new Real(static_cast<double>(x));
  return *this;
}

template<typename T>
JSON::operator T() const {
  json_values typ = this->type();
  if (typ != JSON_INTEGER && typ != JSON_REAL && typ != JSON_BOOLEAN)
    throw JSONException("No typecast available for this JSON object to a Numeric/Boolean type");

  if (!std::numeric_limits<T>::is_specialized)
    throw JSONException("You cannot convert this JSON object to Numeric/Boolean type.");
  
  switch(typ) {
    case JSON_INTEGER: 
      return static_cast<T>( ((Integer*)this->val)->val);
    case JSON_REAL: 
      return static_cast<T>( ((Real*)this->val)->val);
    case JSON_BOOLEAN: 
      return static_cast<T>( ((Boolean*)this->val)->val);
    default: assert(false); // Should never happen (already checked at top)
  }
}

template<typename T>
const JSON& JSON::operator [](const T&x) const {
  return (*(const_cast<const JSON*>(this)))[static_cast<size_t>(x)];
}

#endif
