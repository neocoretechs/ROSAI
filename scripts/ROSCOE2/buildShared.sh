mkdir build && cd build
cmake -DBUILD_SHARED_LIBS=ON -DLLAMA_BUILD_SERVER=OFF ..
make -j14

