#define OPEN_HANDLE_LIST(V)                    \
  V(Template, TemplateInfo)                    \
  V(FunctionTemplate, FunctionTemplateInfo)    \
  V(ObjectTemplate, ObjectTemplateInfo)        \
  V(Signature, SignatureInfo)                  \
  V(AccessorSignature, FunctionTemplateInfo)   \
  V(TypeSwitch, TypeSwitchInfo)                \
  V(Data, Object)                              \
  V(RegExp, JSRegExp)                          \
  V(Object, JSObject)                          \
  V(Array, JSArray)                            \
  V(ArrayBuffer, JSArrayBuffer)                \
  V(ArrayBufferView, JSArrayBufferView)        \
  V(TypedArray, JSTypedArray)                  \
  V(Uint8Array, JSTypedArray)                  \
  V(Uint8ClampedArray, JSTypedArray)           \
  V(Int8Array, JSTypedArray)                   \
  V(Uint16Array, JSTypedArray)                 \
  V(Int16Array, JSTypedArray)                  \
  V(Uint32Array, JSTypedArray)                 \
  V(Int32Array, JSTypedArray)                  \
  V(Float32Array, JSTypedArray)                \
  V(Float64Array, JSTypedArray)                \
  V(DataView, JSDataView)                      \
  V(String, String)                            \
  V(Symbol, Symbol)                            \
  V(Script, JSFunction)                        \
  V(UnboundScript, SharedFunctionInfo)         \
  V(Function, JSFunction)                      \
  V(Message, JSMessageObject)                  \
  V(Context, Context)                          \
  V(External, Object)                          \
  V(StackTrace, JSArray)                       \
  V(StackFrame, JSObject)                      \
  V(DeclaredAccessorDescriptor, DeclaredAccessorDescriptor)


#define DECLARE_OPEN_HANDLE(From, To) \
  static inline v8::internal::Handle<v8::internal::To> \
      OpenHandle(const From* that, bool allow_empty_handle = false);

OPEN_HANDLE_LIST(DECLARE_OPEN_HANDLE)

#undef DECLARE_OPEN_HANDLE



// Implementations of OpenHandle

#define MAKE_OPEN_HANDLE(From, To)                                          \
  v8::internal::Handle<v8::internal::To> Utils::OpenHandle(                 \
    const v8::From* that, bool allow_empty_handle) {                        \
    EXTRA_CHECK(allow_empty_handle || that != NULL);                        \
    EXTRA_CHECK(that == NULL ||                                             \
        (*reinterpret_cast<v8::internal::Object**>(                         \
            const_cast<v8::From*>(that)))->Is##To());                       \
    return v8::internal::Handle<v8::internal::To>(                          \
        reinterpret_cast<v8::internal::To**>(const_cast<v8::From*>(that))); \
  }

OPEN_HANDLE_LIST(MAKE_OPEN_HANDLE)

#undef MAKE_OPEN_HANDLE
#undef OPEN_HANDLE_LIST



