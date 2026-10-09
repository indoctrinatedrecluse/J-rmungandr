#!/usr/bin/env python3
"""
tools/patch_portable_ide.py
Applies full Jörmungandr branding, bootstrap classpath injection,
portable configuration, and runtime customization to a staged IntelliJ directory.
Ensures zero UTF-8 BOM issues and cross-platform JAR path separators.
"""

import os
import sys
import json
import shutil
import time
import zipfile

def patch_portable_ide(staging_dir, resources_dir, tag_name="1.0.0"):
    staging_dir = os.path.abspath(staging_dir)
    resources_dir = os.path.abspath(resources_dir)
    
    print(f"[*] Patching portable IDE at: {staging_dir}")
    print(f"[*] Resources directory: {resources_dir}")
    print(f"[*] Release tag: {tag_name}")

    # 1. Build and install lib/jormungandr-bootstrap.jar
    lib_dir = os.path.join(staging_dir, "lib")
    os.makedirs(lib_dir, exist_ok=True)
    bootstrap_jar = os.path.join(lib_dir, "jormungandr-bootstrap.jar")

    app_info_xml = f'''<component xmlns="http://jetbrains.org/intellij/schema/application-info">
  <version major="2024" minor="3.2"/>
  <company name="indoctrinatedrecluse" url="https://github.com/indoctrinatedrecluse/Jormungandr"/>
  <build number="JM-243.23654.117" date="20261008" majorReleaseDate="20241113"/>
  <logo url="/splash/splash.png"/>
  <icon svg="/icons/jormungandr.svg" svg-small="/icons/jormungandr_16.svg"/>
  <icon-eap svg="/icons/jormungandr.svg" svg-small="/icons/jormungandr_16.svg"/>
  <names product="Jörmungandr" fullname="Jörmungandr" edition="Studio Edition" script="jormungandr" motto="The Autonomous Modular IDE for Data Science &amp; Python"/>

  <essential-plugin>com.intellij.java</essential-plugin>
  <essential-plugin>com.intellij.java.ide</essential-plugin>
  <essential-plugin>com.intellij.modules.json</essential-plugin>
  <essential-plugin>org.jormungandr.ide</essential-plugin>
</component>'''

    print(" -> Creating lib/jormungandr-bootstrap.jar...")
    with zipfile.ZipFile(bootstrap_jar, 'w', compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr('idea/IdeaApplicationInfo.xml', app_info_xml.encode('utf-8'))

        svg_path = os.path.join(resources_dir, 'icons', 'jormungandr.svg')
        if os.path.exists(svg_path):
            with open(svg_path, 'rb') as f:
                svg_data = f.read()
                z.writestr('idea-ce.svg', svg_data)

        svg16_path = os.path.join(resources_dir, 'icons', 'jormungandr_16.svg')
        if os.path.exists(svg16_path):
            with open(svg16_path, 'rb') as f:
                z.writestr('idea-ce_16.svg', f.read())

        splash_path = os.path.join(resources_dir, 'splash', 'splash.png')
        if os.path.exists(splash_path):
            with open(splash_path, 'rb') as f:
                z.writestr('idea_community_logo.png', f.read())

        for folder in ['icons', 'splash', 'themes']:
            full_folder = os.path.join(resources_dir, folder)
            if os.path.isdir(full_folder):
                for root, dirs, files in os.walk(full_folder):
                    for f in files:
                        full_path = os.path.join(root, f)
                        rel_path = os.path.relpath(full_path, resources_dir).replace('\\', '/')
                        z.write(full_path, rel_path)

        for f in os.listdir(resources_dir):
            if f.startswith('consents') and f.endswith('.json'):
                z.write(os.path.join(resources_dir, f), f)

    # 1.5 Patch modules/module-descriptors.jar to register jormungandr-bootstrap.jar
    descriptors_jar = os.path.join(staging_dir, "modules", "module-descriptors.jar")
    if os.path.exists(descriptors_jar):
        print(" -> Patching modules/module-descriptors.jar...")
        tmp_jar = descriptors_jar + ".tmp"
        with zipfile.ZipFile(descriptors_jar, 'r') as zin, zipfile.ZipFile(tmp_jar, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                content = zin.read(item.filename)
                if item.filename == "intellij.idea.community.customization.xml":
                    text = content.decode('utf-8')
                    if "jormungandr-bootstrap.jar" not in text:
                        text = text.replace("<resources>", "<resources>\n    <resource-root path=\"../lib/jormungandr-bootstrap.jar\"/>")
                        text = text.replace("<resources>\r\n", "<resources>\r\n    <resource-root path=\"../lib/jormungandr-bootstrap.jar\"/>\r\n")
                        content = text.encode('utf-8')
                        print("    [+] Injected jormungandr-bootstrap.jar into intellij.idea.community.customization.xml")
                zout.writestr(item, content)
        shutil.move(tmp_jar, descriptors_jar)

    # 1.6 Patch lib/app.jar with Jörmungandr ApplicationInfo and Splash/Branding
    app_jar = os.path.join(staging_dir, "lib", "app.jar")
    if os.path.exists(app_jar):
        print(" -> Patching lib/app.jar...")
        tmp_app_jar = app_jar + ".tmp"
        splash_file = os.path.join(resources_dir, "splash", "splash.png")
        splash2x_file = os.path.join(resources_dir, "splash", "splash@2x.png")
        icon_file = os.path.join(resources_dir, "icons", "jormungandr.svg")
        icon16_file = os.path.join(resources_dir, "icons", "jormungandr_16.svg")

        splash_data = open(splash_file, 'rb').read() if os.path.exists(splash_file) else None
        splash2x_data = open(splash2x_file, 'rb').read() if os.path.exists(splash2x_file) else None
        icon_data = open(icon_file, 'rb').read() if os.path.exists(icon_file) else None
        icon16_data = open(icon16_file, 'rb').read() if os.path.exists(icon16_file) else None

        with zipfile.ZipFile(app_jar, 'r') as zin, zipfile.ZipFile(tmp_app_jar, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                if item.filename == 'idea/IdeaApplicationInfo.xml':
                    zout.writestr(item.filename, app_info_xml.encode('utf-8'))
                    print("    [+] Replaced idea/IdeaApplicationInfo.xml in app.jar")
                elif item.filename == 'idea_community_logo.png' and splash_data:
                    zout.writestr(item.filename, splash_data)
                    print("    [+] Replaced idea_community_logo.png with splash.png in app.jar")
                elif item.filename == 'idea_community_logo@2x.png' and splash2x_data:
                    zout.writestr(item.filename, splash2x_data)
                    print("    [+] Replaced idea_community_logo@2x.png with splash@2x.png in app.jar")
                elif item.filename == '__index__':
                    pass
                else:
                    zout.writestr(item, zin.read(item.filename))

            if splash_data:
                zout.writestr('splash/splash.png', splash_data)
            if splash2x_data:
                zout.writestr('splash/splash@2x.png', splash2x_data)
            if icon_data:
                zout.writestr('icons/jormungandr.svg', icon_data)
            if icon16_data:
                zout.writestr('icons/jormungandr_16.svg', icon16_data)

        shutil.move(tmp_app_jar, app_jar)

    # 1.7 Ensure Jörmungandr plugins are deployed into portable-data/plugins, jormungandr-plugins, and plugins
    plugins_dir = os.path.join(staging_dir, "plugins")
    jorm_plugins_dir = os.path.join(staging_dir, "jormungandr-plugins")
    portable_plugins_dir = os.path.join(staging_dir, "portable-data", "plugins")
    os.makedirs(plugins_dir, exist_ok=True)
    os.makedirs(portable_plugins_dir, exist_ok=True)
    os.makedirs(jorm_plugins_dir, exist_ok=True)

    # Determine available plugin source directory
    src_dir = None
    if os.path.exists(jorm_plugins_dir) and any(os.path.isdir(os.path.join(jorm_plugins_dir, d)) for d in os.listdir(jorm_plugins_dir)):
        src_dir = jorm_plugins_dir
    elif os.path.exists(portable_plugins_dir) and any(os.path.isdir(os.path.join(portable_plugins_dir, d)) for d in os.listdir(portable_plugins_dir)):
        src_dir = portable_plugins_dir
    elif os.path.exists(plugins_dir):
        # Check if platform-shell or jupyter-integration is in plugins_dir
        for cand in ["platform-shell", "jupyter-integration", "dataframe-viewer", "database-suite"]:
            if os.path.isdir(os.path.join(plugins_dir, cand)):
                src_dir = plugins_dir
                break

    if src_dir:
        print(f" -> Deploying Jörmungandr plugins from {src_dir} to portable-data/plugins and backup directories...")
        for item in ["platform-shell", "jupyter-integration", "dataframe-viewer", "database-suite"]:
            src_item = os.path.join(src_dir, item)
            if os.path.isdir(src_item):
                for dest in [portable_plugins_dir, jorm_plugins_dir, plugins_dir]:
                    dest_item = os.path.join(dest, item)
                    if os.path.abspath(src_item) != os.path.abspath(dest_item):
                        if os.path.exists(dest_item):
                            shutil.rmtree(dest_item)
                        shutil.copytree(src_item, dest_item)
                print(f"    [+] Successfully deployed plugin: {item}")

    # 2. Update product-info.json (strict UTF-8 without BOM)
    product_info_path = os.path.join(staging_dir, "product-info.json")
    if os.path.exists(product_info_path):
        print(" -> Updating product-info.json...")
        with open(product_info_path, 'rb') as f:
            raw_bytes = f.read()
            if raw_bytes.startswith(b'\xef\xbb\xbf'):
                raw_bytes = raw_bytes[3:]
            product_info = json.loads(raw_bytes.decode('utf-8'))

        product_info["name"] = "Jörmungandr"
        product_info["productVendor"] = "indoctrinatedrecluse"
        product_info["dataDirectoryName"] = "Jormungandr1.0"
        product_info["svgIconPath"] = "bin/jormungandr.svg"

        if "launch" in product_info and len(product_info["launch"]) > 0:
            launch = product_info["launch"][0]
            cp_jars = launch.get("bootClassPathJarNames", [])
            if "jormungandr-bootstrap.jar" in cp_jars:
                cp_jars.remove("jormungandr-bootstrap.jar")
            # Insert right after platform-loader.jar
            if len(cp_jars) > 0 and cp_jars[0] == "platform-loader.jar":
                cp_jars.insert(1, "jormungandr-bootstrap.jar")
            else:
                cp_jars.insert(0, "jormungandr-bootstrap.jar")
            launch["bootClassPathJarNames"] = cp_jars

            args = launch.get("additionalJvmArguments", [])
            new_args = []
            for a in args:
                if a.startswith("-Didea.vendor.name="):
                    new_args.append("-Didea.vendor.name=indoctrinatedrecluse")
                elif a.startswith("-Didea.paths.selector="):
                    new_args.append("-Didea.paths.selector=Jormungandr1.0")
                elif a.startswith("-Didea.product.name="):
                    continue
                elif a.startswith("-Didea.application.name="):
                    continue
                else:
                    new_args.append(a)
            new_args.append("-Didea.product.name=Jörmungandr")
            new_args.append("-Didea.application.name=Jörmungandr")
            launch["additionalJvmArguments"] = new_args

        with open(product_info_path, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(product_info, f, indent=2, ensure_ascii=False)

    # 3. Write bin/idea.properties (strict ASCII / UTF-8 without BOM)
    bin_dir = os.path.join(staging_dir, "bin")
    os.makedirs(bin_dir, exist_ok=True)
    idea_properties_path = os.path.join(bin_dir, "idea.properties")
    print(" -> Writing bin/idea.properties...")
    props_lines = [
        "# Jörmungandr Standalone Portable IDE Configuration",
        "idea.config.path=${idea.home.path}/portable-data/config",
        "idea.system.path=${idea.home.path}/portable-data/system",
        "idea.plugins.path=${idea.home.path}/portable-data/plugins",
        "idea.log.path=${idea.home.path}/portable-data/log",
        "idea.vendor.name=indoctrinatedrecluse",
        "idea.product.name=Jörmungandr",
        "idea.application.name=Jörmungandr",
        "idea.fatal.error.notification=disabled",
        "sun.java2d.uiScale.enabled=true",
        "swing.bufferPerWindow=true",
        ""
    ]
    with open(idea_properties_path, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(props_lines))

    # 4. Append to bin/idea64.exe.vmoptions & copy to jormungandr64.exe.vmoptions
    vmoptions_path = os.path.join(bin_dir, "idea64.exe.vmoptions")
    print(" -> Updating bin/idea64.exe.vmoptions...")
    vm_additions = [
        "",
        "-Didea.vendor.name=indoctrinatedrecluse",
        "-Didea.product.name=Jörmungandr",
        "-Didea.application.name=Jörmungandr",
        "-Didea.platform.prefix=Idea",
        "-Didea.paths.selector=Jormungandr1.0",
        "-Didea.required.plugins.id=org.jormungandr.ide",
        "-Djb.consents.confirmation.enabled=false",
        "-Deua.consents.confirmation.enabled=false",
        "-Didea.initially.ask.config=false",
        "-Dide.show.tips.on.startup=false",
        "-Dide.mac.message.dialogs.as.sheets=false",
        "-Dwsl.use.remote.agent.for.nio.filesystem=false",
        "-Dwsl.enabled=false",
        "-Dide.ijent.wsldefault=false",
        "-Didea.wsl.support.enabled=false",
        "-Dsplash=true",
        ""
    ]
    if os.path.exists(vmoptions_path):
        with open(vmoptions_path, 'r', encoding='utf-8', errors='ignore') as f:
            existing_vm = f.read()
        if "-Didea.product.name=Jörmungandr" not in existing_vm:
            with open(vmoptions_path, 'a', encoding='utf-8', newline='\n') as f:
                f.write('\n'.join(vm_additions))

    # Create jormungandr64.exe and jormungandr64.exe.vmoptions
    idea64_exe = os.path.join(bin_dir, "idea64.exe")
    jorm_exe = os.path.join(bin_dir, "jormungandr64.exe")
    jorm_vmoptions = os.path.join(bin_dir, "jormungandr64.exe.vmoptions")
    if os.path.exists(idea64_exe):
        shutil.copy2(idea64_exe, jorm_exe)
    if os.path.exists(vmoptions_path):
        shutil.copy2(vmoptions_path, jorm_vmoptions)

    # 5. Patch bin/idea.bat
    idea_bat = os.path.join(bin_dir, "idea.bat")
    if os.path.exists(idea_bat):
        print(" -> Patching bin/idea.bat...")
        with open(idea_bat, 'r', encoding='latin1') as f:
            bat_lines = f.readlines()
        new_bat = []
        for line in bat_lines:
            if line.strip().upper() == '@ECHO OFF':
                new_bat.append(line)
                new_bat.append('if not exist "%IDE_HOME%\\portable-data\\plugins\\platform-shell" (\n')
                new_bat.append('    if exist "%IDE_HOME%\\jormungandr-plugins" (\n')
                new_bat.append('        xcopy /E /I /Q /Y "%IDE_HOME%\\jormungandr-plugins" "%IDE_HOME%\\portable-data\\plugins" >nul 2>&1\n')
                new_bat.append('    )\n')
                new_bat.append(')\n')
                continue
            new_bat.append(line)
            if 'SET "CLASS_PATH=%IDE_HOME%\\lib\\platform-loader.jar"' in line:
                new_bat.append('SET "CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\\lib\\jormungandr-bootstrap.jar"\n')
        with open(idea_bat, 'w', encoding='latin1', newline='\r\n') as f:
            f.writelines(new_bat)

    # 6. Copy launcher icons in bin
    ico_src = os.path.join(resources_dir, "icons", "jormungandr.ico")
    svg_src = os.path.join(resources_dir, "icons", "jormungandr.svg")
    for dst_name in ["idea.ico", "jormungandr.ico"]:
        if os.path.exists(ico_src):
            shutil.copy2(ico_src, os.path.join(bin_dir, dst_name))
    for dst_name in ["idea.svg", "jormungandr.svg"]:
        if os.path.exists(svg_src):
            shutil.copy2(svg_src, os.path.join(bin_dir, dst_name))

    # 7. Pre-populate portable consent & configuration
    portable_data = os.path.join(staging_dir, "portable-data")
    options_dir = os.path.join(portable_data, "config", "options")
    os.makedirs(options_dir, exist_ok=True)
    os.makedirs(os.path.join(portable_data, "system"), exist_ok=True)
    os.makedirs(os.path.join(portable_data, "log"), exist_ok=True)
    os.makedirs(os.path.join(portable_data, "plugins"), exist_ok=True)

    now_ms = int(time.time() * 1000)
    accepted_str = f"rsch.send.usage.stat:1.1:0:{now_ms};eap:2021.2:0:{now_ms};\n"
    cached_json = '[{"consentId":"rsch.send.usage.stat","version":"1.1","text":"Help improve Jörmungandr.","printableName":"Send Usage Statistics","accepted":"false"},{"consentId":"eap","version":"2021.2","text":"Surveys","printableName":"Feedback","accepted":"false"}]'

    consent_paths = [
        os.path.join(portable_data, "config", "consentOptions"),
        os.path.join(portable_data, "config", "jormungandr", "consentOptions"),
        os.path.join(portable_data, "config", "idea", "consentOptions")
    ]
    for cp in consent_paths:
        os.makedirs(cp, exist_ok=True)
        with open(os.path.join(cp, "accepted"), 'w', encoding='utf-8', newline='\n') as f:
            f.write(accepted_str)
        with open(os.path.join(cp, "cached"), 'w', encoding='utf-8', newline='\n') as f:
            f.write(cached_json)

    other_xml = '''<application>
  <component name="PropertyService"><![CDATA[{
  "keyToString": {
    "eua_accepted_version": "2.0",
    "privacy_policy_accepted_version": "2.0",
    "previous_eua_accepted_version": "2.0",
    "ask.about.tip.of.the.day": "false",
    "show.tips.on.startup": "false"
  }
}]]></component>
</application>'''
    with open(os.path.join(options_dir, "other.xml"), 'w', encoding='utf-8', newline='\n') as f:
        f.write(other_xml)

    laf_xml = '''<application>
  <component name="LafManager" autodetect="false">
    <laf themeId="org.jormungandr.theme.solarized.light" />
    <preferred-light-laf themeId="org.jormungandr.theme.solarized.light" />
    <preferred-dark-laf themeId="org.jormungandr.theme.bubblegum.barbie" />
  </component>
</application>'''
    with open(os.path.join(options_dir, "laf.xml"), 'w', encoding='utf-8', newline='\n') as f:
        f.write(laf_xml)

    # 8. Create root launcher Jormungandr.bat with plugin auto-heal
    launcher_bat = os.path.join(staging_dir, "Jormungandr.bat")
    bat_content = '''@echo off
if not exist "%~dp0portable-data\\plugins\\platform-shell" (
    if exist "%~dp0jormungandr-plugins" (
        xcopy /E /I /Q /Y "%~dp0jormungandr-plugins" "%~dp0portable-data\\plugins" >nul 2>&1
    )
)
start "" "%~dp0bin\\idea64.exe" %*
'''
    with open(launcher_bat, 'w', encoding='ascii', newline='\r\n') as f:
        f.write(bat_content)

    print("[+] Staging patched successfully with 100% turnkey Jörmungandr branding and runtime config!")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: patch_portable_ide.py <staging_dir> <resources_dir> [tag_name]")
        sys.exit(1)
    tag = sys.argv[3] if len(sys.argv) > 3 else "1.0.0"
    patch_portable_ide(sys.argv[1], sys.argv[2], tag)
