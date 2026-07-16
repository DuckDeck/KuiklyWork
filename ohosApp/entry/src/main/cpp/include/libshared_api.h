#ifndef KONAN_LIBSHARED_H
#define KONAN_LIBSHARED_H
#ifdef __cplusplus
extern "C" {
#endif
#ifdef __cplusplus
typedef bool            libshared_KBoolean;
#else
typedef _Bool           libshared_KBoolean;
#endif
typedef unsigned short     libshared_KChar;
typedef signed char        libshared_KByte;
typedef short              libshared_KShort;
typedef int                libshared_KInt;
typedef long long          libshared_KLong;
typedef unsigned char      libshared_KUByte;
typedef unsigned short     libshared_KUShort;
typedef unsigned int       libshared_KUInt;
typedef unsigned long long libshared_KULong;
typedef float              libshared_KFloat;
typedef double             libshared_KDouble;
typedef float __attribute__ ((__vector_size__ (16))) libshared_KVector128;
typedef void*              libshared_KNativePtr;
struct libshared_KType;
typedef struct libshared_KType libshared_KType;

typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Byte;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Short;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Int;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Long;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Float;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Double;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Char;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Boolean;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Unit;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_UByte;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_UShort;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_UInt;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_ULong;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_ss_kuiklywork_NetbianDetail;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_Any;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_ss_kuiklywork_ImageItem;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_ss_kuiklywork_CategoryItem;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_com_ss_kuiklywork_CategoryCache;
typedef struct {
  libshared_KNativePtr pinned;
} libshared_kref_kotlin_collections_List;


typedef struct {
  /* Service functions. */
  void (*DisposeStablePointer)(libshared_KNativePtr ptr);
  void (*DisposeString)(const char* string);
  libshared_KBoolean (*IsInstance)(libshared_KNativePtr ref, const libshared_KType* type);
  libshared_kref_kotlin_Byte (*createNullableByte)(libshared_KByte);
  libshared_KByte (*getNonNullValueOfByte)(libshared_kref_kotlin_Byte);
  libshared_kref_kotlin_Short (*createNullableShort)(libshared_KShort);
  libshared_KShort (*getNonNullValueOfShort)(libshared_kref_kotlin_Short);
  libshared_kref_kotlin_Int (*createNullableInt)(libshared_KInt);
  libshared_KInt (*getNonNullValueOfInt)(libshared_kref_kotlin_Int);
  libshared_kref_kotlin_Long (*createNullableLong)(libshared_KLong);
  libshared_KLong (*getNonNullValueOfLong)(libshared_kref_kotlin_Long);
  libshared_kref_kotlin_Float (*createNullableFloat)(libshared_KFloat);
  libshared_KFloat (*getNonNullValueOfFloat)(libshared_kref_kotlin_Float);
  libshared_kref_kotlin_Double (*createNullableDouble)(libshared_KDouble);
  libshared_KDouble (*getNonNullValueOfDouble)(libshared_kref_kotlin_Double);
  libshared_kref_kotlin_Char (*createNullableChar)(libshared_KChar);
  libshared_KChar (*getNonNullValueOfChar)(libshared_kref_kotlin_Char);
  libshared_kref_kotlin_Boolean (*createNullableBoolean)(libshared_KBoolean);
  libshared_KBoolean (*getNonNullValueOfBoolean)(libshared_kref_kotlin_Boolean);
  libshared_kref_kotlin_Unit (*createNullableUnit)(void);
  libshared_kref_kotlin_UByte (*createNullableUByte)(libshared_KUByte);
  libshared_KUByte (*getNonNullValueOfUByte)(libshared_kref_kotlin_UByte);
  libshared_kref_kotlin_UShort (*createNullableUShort)(libshared_KUShort);
  libshared_KUShort (*getNonNullValueOfUShort)(libshared_kref_kotlin_UShort);
  libshared_kref_kotlin_UInt (*createNullableUInt)(libshared_KUInt);
  libshared_KUInt (*getNonNullValueOfUInt)(libshared_kref_kotlin_UInt);
  libshared_kref_kotlin_ULong (*createNullableULong)(libshared_KULong);
  libshared_KULong (*getNonNullValueOfULong)(libshared_kref_kotlin_ULong);

  /* User functions. */
  struct {
    struct {
      struct {
        struct {
          struct {
            struct {
              libshared_KType* (*_type)(void);
              libshared_kref_com_ss_kuiklywork_NetbianDetail (*NetbianDetail)(const char* imageUrl, const char* downloadUrl, const char* title, const char* categoryName, const char* categoryUrl, const char* size, const char* fileSize, const char* updatedAt);
              const char* (*get_categoryName)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              const char* (*get_categoryUrl)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              const char* (*get_downloadUrl)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              const char* (*get_fileSize)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              const char* (*get_imageUrl)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              const char* (*get_size)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              const char* (*get_title)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              const char* (*get_updatedAt)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              const char* (*component1)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              const char* (*component2)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              const char* (*component3)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              const char* (*component4)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              const char* (*component5)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              const char* (*component6)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              const char* (*component7)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              const char* (*component8)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              libshared_kref_com_ss_kuiklywork_NetbianDetail (*copy)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz, const char* imageUrl, const char* downloadUrl, const char* title, const char* categoryName, const char* categoryUrl, const char* size, const char* fileSize, const char* updatedAt);
              libshared_KBoolean (*equals)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz, libshared_kref_kotlin_Any other);
              libshared_KInt (*hashCode)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
              const char* (*toString)(libshared_kref_com_ss_kuiklywork_NetbianDetail thiz);
            } NetbianDetail;
            struct {
              libshared_KType* (*_type)(void);
              libshared_kref_com_ss_kuiklywork_ImageItem (*ImageItem)(const char* url, const char* title, const char* detailUrl, libshared_KBoolean downloaded);
              const char* (*get_detailUrl)(libshared_kref_com_ss_kuiklywork_ImageItem thiz);
              libshared_KBoolean (*get_downloaded)(libshared_kref_com_ss_kuiklywork_ImageItem thiz);
              const char* (*get_title)(libshared_kref_com_ss_kuiklywork_ImageItem thiz);
              const char* (*get_url)(libshared_kref_com_ss_kuiklywork_ImageItem thiz);
              const char* (*component1)(libshared_kref_com_ss_kuiklywork_ImageItem thiz);
              const char* (*component2)(libshared_kref_com_ss_kuiklywork_ImageItem thiz);
              const char* (*component3)(libshared_kref_com_ss_kuiklywork_ImageItem thiz);
              libshared_KBoolean (*component4)(libshared_kref_com_ss_kuiklywork_ImageItem thiz);
              libshared_kref_com_ss_kuiklywork_ImageItem (*copy)(libshared_kref_com_ss_kuiklywork_ImageItem thiz, const char* url, const char* title, const char* detailUrl, libshared_KBoolean downloaded);
              libshared_KBoolean (*equals)(libshared_kref_com_ss_kuiklywork_ImageItem thiz, libshared_kref_kotlin_Any other);
              libshared_KInt (*hashCode)(libshared_kref_com_ss_kuiklywork_ImageItem thiz);
              const char* (*toString)(libshared_kref_com_ss_kuiklywork_ImageItem thiz);
            } ImageItem;
            struct {
              libshared_KType* (*_type)(void);
              libshared_kref_com_ss_kuiklywork_CategoryItem (*CategoryItem)(const char* name, const char* path);
              const char* (*get_name)(libshared_kref_com_ss_kuiklywork_CategoryItem thiz);
              const char* (*get_path)(libshared_kref_com_ss_kuiklywork_CategoryItem thiz);
              const char* (*component1)(libshared_kref_com_ss_kuiklywork_CategoryItem thiz);
              const char* (*component2)(libshared_kref_com_ss_kuiklywork_CategoryItem thiz);
              libshared_kref_com_ss_kuiklywork_CategoryItem (*copy)(libshared_kref_com_ss_kuiklywork_CategoryItem thiz, const char* name, const char* path);
              libshared_KBoolean (*equals)(libshared_kref_com_ss_kuiklywork_CategoryItem thiz, libshared_kref_kotlin_Any other);
              libshared_KInt (*hashCode)(libshared_kref_com_ss_kuiklywork_CategoryItem thiz);
              const char* (*toString)(libshared_kref_com_ss_kuiklywork_CategoryItem thiz);
            } CategoryItem;
            struct {
              libshared_KType* (*_type)(void);
              libshared_kref_com_ss_kuiklywork_CategoryCache (*CategoryCache)(libshared_kref_kotlin_collections_List items, libshared_KInt pageIndex, libshared_KBoolean noMore);
              libshared_kref_kotlin_collections_List (*get_items)(libshared_kref_com_ss_kuiklywork_CategoryCache thiz);
              libshared_KBoolean (*get_noMore)(libshared_kref_com_ss_kuiklywork_CategoryCache thiz);
              libshared_KInt (*get_pageIndex)(libshared_kref_com_ss_kuiklywork_CategoryCache thiz);
              libshared_kref_kotlin_collections_List (*component1)(libshared_kref_com_ss_kuiklywork_CategoryCache thiz);
              libshared_KInt (*component2)(libshared_kref_com_ss_kuiklywork_CategoryCache thiz);
              libshared_KBoolean (*component3)(libshared_kref_com_ss_kuiklywork_CategoryCache thiz);
              libshared_kref_com_ss_kuiklywork_CategoryCache (*copy)(libshared_kref_com_ss_kuiklywork_CategoryCache thiz, libshared_kref_kotlin_collections_List items, libshared_KInt pageIndex, libshared_KBoolean noMore);
              libshared_KBoolean (*equals)(libshared_kref_com_ss_kuiklywork_CategoryCache thiz, libshared_kref_kotlin_Any other);
              libshared_KInt (*hashCode)(libshared_kref_com_ss_kuiklywork_CategoryCache thiz);
              const char* (*toString)(libshared_kref_com_ss_kuiklywork_CategoryCache thiz);
            } CategoryCache;
          } kuiklywork;
        } ss;
      } com;
      libshared_KInt (*initKuikly)();
    } root;
  } kotlin;
} libshared_ExportedSymbols;
extern libshared_ExportedSymbols* libshared_symbols(void);
#ifdef __cplusplus
}  /* extern "C" */
#endif
#endif  /* KONAN_LIBSHARED_H */
