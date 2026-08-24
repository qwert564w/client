#!/usr/bin/env python3
import os
import subprocess
import tarfile
import urllib.request
import shutil

def install_java21():
    java_url = "https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_linux-x64_bin.tar.gz"
    download_path = "/tmp/openjdk-21.tar.gz"
    extract_path = "/opt"
    java_home = "/opt/jdk-21"
    
    print("Downloading Java 21...")
    urllib.request.urlretrieve(java_url, download_path)
    
    print("Extracting Java 21...")
    with tarfile.open(download_path, "r:gz") as tar:
        tar.extractall(extract_path)
    
    # Rename to jdk-21
    extracted_dir = os.path.join(extract_path, "jdk-21.0.2")
    if os.path.exists(extracted_dir):
        shutil.move(extracted_dir, java_home)
    
    # Set environment variables
    env_vars = f"""
export JAVA_HOME={java_home}
export PATH={java_home}/bin:$PATH
"""
    
    with open("/etc/profile.d/java21.sh", "w") as f:
        f.write(env_vars)
    
    os.chmod("/etc/profile.d/java21.sh", 0o755)
    
    # Also set for current session
    os.environ["JAVA_HOME"] = java_home
    os.environ["PATH"] = f"{java_home}/bin:" + os.environ.get("PATH", "")
    
    print("Java 21 installed successfully!")
    print(f"JAVA_HOME: {java_home}")
    
    # Verify installation
    result = subprocess.run([f"{java_home}/bin/java", "-version"], capture_output=True, text=True)
    print(result.stderr)

if __name__ == "__main__":
    install_java21()
