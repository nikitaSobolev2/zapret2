DIRS := nfq2 ip2net mdig
# macos has no packet intercept facility (ipdivert removed, pf has no divert-packet), so nfq2 is not built
DIRS_MAC := tpws ip2net mdig
DIRS_CLEAN := nfq2 tpws ip2net mdig
TGT := binaries/my

# $(1) - source dirs, $(2) - target to make inside each dir (empty = default)
define build_dirs
	@mkdir -p "$(TGT)"; \
	for dir in $(1); do \
		find "$$dir" -type f  \( -name "*.c" -o -name "*.h" -o -name "*akefile" \) -exec chmod -x {} \; ; \
		$(MAKE) -C "$$dir" $(2) || exit; \
		for exe in "$$dir/"*; do \
			if [ -f "$$exe" ] && [ -x "$$exe" ]; then \
				mv -f "$$exe" "${TGT}" ; \
				ln -fs "../${TGT}/$$(basename "$$exe")" "$$exe" ; \
			fi \
		done \
	done
endef

# $(1) - source dirs
define clean_dirs
	@[ -d "$(TGT)" ] && rm -rf "$(TGT)" ; \
	for dir in $(1); do \
		$(MAKE) -C "$$dir" clean; \
	done
endef

all:	clean
	$(call build_dirs,$(DIRS),)

systemd: clean
	$(call build_dirs,$(DIRS),systemd)

android: clean
	$(call build_dirs,$(DIRS),android)

bsd:	clean
	$(call build_dirs,$(DIRS),bsd)

mac:	clean-mac
	$(call build_dirs,$(DIRS_MAC),mac)

clean:
	$(call clean_dirs,$(DIRS_CLEAN))

# nfq2 makefile probes for lua at parse time and fails on macos, so it is left out
clean-mac:
	$(call clean_dirs,$(DIRS_MAC))

# Compose Desktop control app (JDK 21+)
# Homebrew: brew tap nikitaSobolev2/zapret2 https://github.com/nikitaSobolev2/zapret2 && brew install --cask zapret
# optional version: APP_VERSION=1.0.1 make app-dmg
APP_VERSION ?=

app:
	cd app && ./gradlew run

app-package:
	cd app && ./gradlew createDistributable

app-dmg:
	cd app && ./gradlew packageDmg $(if $(APP_VERSION),-PappVersion=$(APP_VERSION),)
