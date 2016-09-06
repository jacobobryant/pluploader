cd "$(dirname "${BASH_SOURCE[0]}")"
srcdir=app/src/main/java/com/jacobobryant/musicrecommender
sed -ri 's/^(\s*)Log/\1if (BuildConfig.DEBUG) Log/' $srcdir/*.java
