ANDROID_HOME ?= $(HOME)/Android/Sdk
NDK_HOME ?= $(ANDROID_HOME)/ndk/27.2.12479018
GOMOBILE := $(HOME)/go/bin/gomobile
GOBIND := $(HOME)/go/bin/gobind
export PATH := $(HOME)/go/bin:$(PATH)
MODULE_DIR := ../modules/upeer-transport/android

.PHONY: test bind-android

test:
	go test ./...

bind-android:
	ANDROID_HOME=$(ANDROID_HOME) ANDROID_NDK_HOME=$(NDK_HOME) $(GOMOBILE) bind -target=android/arm64 -androidapi=23 -javapkg=app.upeer.transportbridge -o $(MODULE_DIR)/libs/upeer-transport.aar .
	unzip -p $(MODULE_DIR)/libs/upeer-transport.aar classes.jar > $(MODULE_DIR)/libs/upeer-transport-classes.jar
	rm -rf $(MODULE_DIR)/src/main/java/app/upeer/transportbridge $(MODULE_DIR)/src/main/java/go $(MODULE_DIR)/src/main/java/transportbridge $(MODULE_DIR)/src/main/java/META-INF
	@mkdir -p $(MODULE_DIR)/src/main/java $(MODULE_DIR)/src/main/jniLibs/arm64-v8a
	@tmp=$$(mktemp -d) && $(GOBIND) -lang=java -javapkg=app.upeer.transportbridge -outdir $$tmp github.com/upeer/upeer/transport-bridge && cp -R $$tmp/java/. $(MODULE_DIR)/src/main/java/ && jar cf $(MODULE_DIR)/libs/upeer-transport-sources.jar -C $$tmp/java . && rm -rf $$tmp
	find $(MODULE_DIR)/src/main/java/app/upeer/transportbridge -name '*.java' -exec sed -i 's/[[:space:]]*$$//' {} +
	unzip -p $(MODULE_DIR)/libs/upeer-transport.aar jni/arm64-v8a/libgojni.so > $(MODULE_DIR)/src/main/jniLibs/arm64-v8a/libgojni.so
	rm -f $(MODULE_DIR)/src/main/jniLibs/arm64-v8a/libupeertransport.so
