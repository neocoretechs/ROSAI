sudo apt-get update
sudo apt-get install -y cmake build-essential libcurl4-openssl-dev
export PATH=/usr/local/cuda/bin${PATH:+:${PATH}}
export LD_LIBRARY_PATH=/usr/local/cuda/lib64${LD_LIBRARY_PATH:+:${LD_LIBRARY_PATH}}
sudo apt install nano curl python3-pip
sudo apt install nvidia-jetpack
sudo apt install -y git libopenblas-dev
#rm -rf build
sudo apt install ninja
sudo apt install -y ninja-build
ninja --version
cmake -S . -B build -G Ninja -DGGML_CUDA=ON -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -DCMAKE_CUDA_ARCHITECTURES=87
cmake --build build
