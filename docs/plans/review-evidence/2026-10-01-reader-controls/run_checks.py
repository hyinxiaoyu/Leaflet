#!/usr/bin/env python3
"""Offline component proof using frozen cached dependencies; all scratch outputs self-clean in /tmp."""
from __future__ import annotations

import argparse
import hashlib
import json
import logging
import os
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--format', action='store_true', help='Format only this fix\'s Kotlin files before checking')
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[4]
    evidence = Path(__file__).resolve().parent
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
    if not args.java.is_file():
        parser.error('Missing java executable')

    def jar(group: str, artifact: str, version: str) -> str:
        paths = list((cache / group / artifact / version).glob('*/*.jar'))
        if len(paths) != 1:
            raise RuntimeError(f'Expected one JAR: {group}:{artifact}:{version}')
        return str(paths[0])

    compiler = [jar('org.jetbrains.kotlin', name, '2.0.21') for name in
                ['kotlin-compiler-embeddable', 'kotlin-stdlib', 'kotlin-script-runtime']]
    compiler += [jar('org.jetbrains.intellij.deps', 'trove4j', '1.0.20200330'),
                 jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.10.2'),
                 jar('org.jetbrains', 'annotations', '13.0')]
    compile_base = [str(args.java), '-Xmx2g', '-cp', ':'.join(compiler),
                    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17']
    compose_plugin = jar('org.jetbrains.kotlin', 'kotlin-compose-compiler-plugin-embeddable', '2.0.21')
    core = repo / 'core/reader/src/main/kotlin/io/leaflet/reader'
    core_sources = [core / (name + '.kt') for name in
                    ['PageGesture', 'PageTurn', 'TapZones', 'GestureArbitration', 'ReadingMode', 'TocNavigation']]
    tests = [repo / 'core/reader/src/test/kotlin/io/leaflet/reader' / name for name in
             ['PageGestureTest.kt', 'PageTurnTest.kt']]
    reader_dir = repo / 'app/src/main/kotlin/io/leaflet/reader/ui/reader'
    readers = [path for path in sorted(reader_dir.glob('*.kt')) if path.stem not in ['RecentSection', 'RecentViewModel']]
    page_dir = repo / 'app/src/androidTest/kotlin/io/leaflet/reader/ui'
    pages = [page_dir / name for name in ['PageHarness.kt', 'PageViews.kt', 'ReaderScreenShim.kt', 'ReaderInteractionTest.kt', 'ReaderControlsPageTest.kt']]
    defaults = repo / 'core/storage/src/main/kotlin/io/leaflet/storage/prefs/ReaderSettings.kt'
    store = repo / 'app/src/main/kotlin/io/leaflet/reader/SettingsStore.kt'
    defaults_test = repo / 'core/storage/src/test/kotlin/io/leaflet/storage/prefs/ReaderSettingsTest.kt'
    store_test = repo / 'app/src/test/kotlin/io/leaflet/reader/SettingsStoreReadingModeTest.kt'
    core_sources += [defaults, store]
    tests += [defaults_test, store_test]
    menu_test = repo / 'app/src/test/kotlin/io/leaflet/reader/ui/reader/ReaderMenuMotionTest.kt'
    changed = [menu_test, core / 'PageGesture.kt', tests[0], reader_dir / 'ReaderPageGestures.kt',
               reader_dir / 'ReaderScreen.kt', reader_dir / 'ReaderControls.kt', pages[-1]]
    checks: dict[str, int] = {}

    with tempfile.TemporaryDirectory(prefix='leaflet-reader-controls-', dir='/tmp') as scratch_name:
        scratch = Path(scratch_name)

        def run(label: str, cmd: list[str], env=None) -> int:
            result = subprocess.run(cmd, cwd=repo, env=env, capture_output=True, text=True, timeout=180)
            (evidence / (label + '.txt')).write_text(result.stdout + result.stderr)
            checks[label] = result.returncode
            print(label, result.returncode, flush=True)
            if result.returncode:
                print((result.stdout + result.stderr)[-4500:], flush=True)
            return result.returncode

        # Use ktlint's actual published dependency graph, including its Kotlin 1.9.10 runtime.
        lint_jars: list[str] = []
        visited = set()

        def lint_dependency(group: str, artifact: str, version: str) -> None:
            key = (group, artifact, version)
            if key in visited:
                return
            visited.add(key)
            directory = cache / group / artifact / version
            lint_jars.extend(str(path) for path in directory.glob('*/*.jar'))
            poms = list(directory.glob('*/*.pom'))
            if poms:
                ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
                for dep in ET.parse(poms[0]).findall('./m:dependencies/m:dependency', ns):
                    if dep.findtext('m:scope', default='', namespaces=ns) in ['test', 'provided']:
                        continue
                    parts = [dep.findtext('m:' + name, namespaces=ns) for name in ['groupId', 'artifactId', 'version']]
                    if all(parts) and not any('${' in part for part in parts):
                        lint_dependency(*parts)
        lint_dependency('com.pinterest.ktlint', 'ktlint-cli', '1.0.1')
        # Logback's parent declares its matching core/slf4j dependencies.
        for group, artifact, version in [('ch.qos.logback', 'logback-core', '1.3.5'), ('org.slf4j', 'slf4j-api', '2.0.4')]:
            lint_jars.append(jar(group, artifact, version))
        editorconfig = scratch / '.editorconfig'
        editorconfig.write_text('[*.{kt,kts}]\nktlint_standard_function-naming = disabled\n')
        lint_cmd = [str(args.java), '-cp', ':'.join(lint_jars), 'com.pinterest.ktlint.Main',
                    '--editorconfig=' + str(editorconfig), *map(str, changed)]
        if args.format and run('changed-ktlint-format', lint_cmd + ['--format']):
            return 1
        if run('changed-ktlint', lint_cmd):
            return 1

        def libraries(flavor: str, android_test=False) -> list[str]:
            variant = flavor + 'Debug'
            task = 'lintAnalyze' + flavor.capitalize() + 'Debug' + ('AndroidTest' if android_test else '')
            file = repo / f'app/build/intermediates/incremental/{task}/{variant}-artifact-libraries.xml'
            return [part for library in ET.parse(file).getroot() for part in library.get('jars', '').split(':') if part]

        boot = ET.parse(repo / 'app/build/intermediates/lint_report_lint_model/fullDebug/generateFullDebugLintReportModel/module.xml').getroot().get('bootClassPath').split(':')
        full_cp = libraries('full') + boot + [jar('junit', 'junit', '4.13.2'), jar('org.hamcrest', 'hamcrest-core', '1.3'), jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-test-jvm', '1.10.2')]
        core_out = scratch / 'core'
        if run('core-compile', compile_base + ['-classpath', ':'.join(full_cp), '-d', str(core_out), *map(str, core_sources + tests)]):
            return 1
        classes = ['PageGestureTest', 'PageTurnTest', 'TapZonesTest', 'GestureArbitrationTest', 'TocNavigationTest', 'ReadingModeTest']
        if run('jvm-contracts', [str(args.java), '-cp', ':'.join([str(core_out), *full_cp]), 'org.junit.runner.JUnitCore',
                                 *['io.leaflet.reader.' + name for name in classes], 'io.leaflet.storage.prefs.ReaderSettingsTest', 'io.leaflet.reader.SettingsStoreReadingModeTest']):
            return 1
        sdk = Path(boot[0]).parents[2]
        aapt = sdk / 'build-tools/35.0.0/aapt2'
        dependency_zips = []
        resource_folders = set()
        xml = repo / 'app/build/intermediates/incremental/lintAnalyzeFullDebug/fullDebug-artifact-libraries.xml'
        for library in ET.parse(xml).getroot():
            if library.get('folder') and library.get('resFolder'):
                folder = Path(library.get('folder')) / library.get('resFolder')
                if folder.is_dir():
                    resource_folders.add(folder)
        dependency_log = []
        for index, folder in enumerate(sorted(resource_folders)):
            archive = scratch / f'dependency-{index}.zip'
            result = subprocess.run([str(aapt), 'compile', '--dir', str(folder), '-o', str(archive)], capture_output=True, text=True, check=False)
            dependency_log.append(str(folder) + ': ' + str(result.returncode) + '\n' + result.stdout + result.stderr)
            if result.returncode:
                raise RuntimeError('Dependency resource compile failed: ' + result.stderr)
            dependency_zips.append(archive)
        (evidence / 'dependency-resource-compile.txt').write_text('\n'.join(dependency_log))
        checks['dependency-resources'] = 0
        for locale in ['values', 'values-zh']:
            if run('resources-' + locale, [str(aapt), 'compile', str(repo / f'app/src/main/res/{locale}/strings.xml'), '-o', str(scratch)]):
                return 1
        new_flats = [scratch / f'{locale}_strings.arsc.flat' for locale in ['values', 'values-zh']]
        for flavor in ['full', 'eink']:
            variant = flavor + 'Debug'
            app_jar = repo / f'app/build/intermediates/compile_app_classes_jar/{variant}/bundle{flavor.capitalize()}DebugClassesToCompileJar/classes.jar'
            resource_out = scratch / (flavor + '-resources')
            resource_out.mkdir()
            flavor_flat = resource_out / 'values_strings.arsc.flat'
            if run(flavor + '-flavor-resource', [str(aapt), 'compile', str(repo / f'app/src/{flavor}/res/values/strings.xml'), '-o', str(resource_out)]):
                return 1
            merged = repo / f'app/build/intermediates/merged_res/{variant}/merge{flavor.capitalize()}DebugResources'
            manifests = list((repo / f'app/build/intermediates/packaged_manifests/{variant}').glob('*/AndroidManifest.xml'))
            if len(manifests) != 1:
                raise RuntimeError('Expected one existing packaged manifest')
            link = [str(aapt), 'link', '--auto-add-overlay', '-I', boot[0], '--manifest', str(manifests[0]),
                    '--java', str(resource_out / 'java'), '--custom-package', 'io.leaflet.reader',
                    '-o', str(resource_out / 'resources.apk')]
            for resource in [*dependency_zips, *sorted(merged.glob('*.flat')), *new_flats, flavor_flat]:
                link += ['-R', str(resource)]
            if run(flavor + '-resource-link', link):
                return 1
            r_classes = resource_out / 'r-classes'
            if run(flavor + '-r-compile', [str(args.java.with_name('javac')), '-d', str(r_classes),
                                          *map(str, (resource_out / 'java').rglob('*.java'))]):
                return 1
            cp = [str(core_out), str(r_classes), str(app_jar), *libraries(flavor), *boot]
            app_out = scratch / flavor
            if run(flavor + '-reader-compile', compile_base + ['-Xplugin=' + compose_plugin, '-Xfriend-paths=' + str(app_jar),
                    '-classpath', ':'.join(cp), '-d', str(app_out), *map(str, readers)]):
                return 1
            if flavor == 'full':
                menu_out = scratch / 'menu-contracts'
                menu_cp = [str(app_out), *cp, jar('junit', 'junit', '4.13.2'), jar('org.hamcrest', 'hamcrest-core', '1.3')]
                if run('menu-contract-compile', compile_base + ['-Xfriend-paths=' + str(app_out),
                        '-classpath', ':'.join(menu_cp), '-d', str(menu_out), str(menu_test)]):
                    return 1
                if run('menu-motion-contract', [str(args.java), '-cp', ':'.join([str(menu_out), *menu_cp]),
                        'org.junit.runner.JUnitCore', 'io.leaflet.reader.ui.reader.ReaderMenuMotionTest']):
                    return 1
            if run(flavor + '-interaction-compile', compile_base + ['-Xplugin=' + compose_plugin,
                    '-Xfriend-paths=' + ','.join([str(app_jar), str(app_out)]), '-classpath', ':'.join([str(app_out), *cp, *libraries(flavor, True)]),
                    '-d', str(scratch / (flavor + '-pages')), *map(str, pages)]):
                return 1
        gradle = list((Path.home() / '.gradle/wrapper/dists/gradle-8.13-bin').glob('*/gradle-8.13/bin/gradle'))[0]
        env = dict(os.environ, JAVA_HOME=str(args.java.parent.parent), GRADLE_USER_HOME=str(scratch / 'gradle'))
        run('gradle-gate', [str(gradle), '--offline', '--no-daemon', ':app:assembleFullDebug', ':app:assembleEinkDebug'], env)
        hashes = {str(path.relative_to(repo)): hashlib.sha256(path.read_bytes()).hexdigest() for path in changed + [repo / 'app/src/main/res/values/strings.xml', repo / 'app/src/main/res/values-zh/strings.xml']}
        summary = {'checks': checks, 'source_sha256': hashes, 'android_tests': 'COMPILED_ONLY; no device execution',
                   'scope': 'Fresh core/Reader/interaction/control component compilation and actual resource linking over cached resolved dependencies; not APK assembly',
                   'cleanup': 'TemporaryDirectory removes all compiler/resource/Gradle outputs on exit'}
        (evidence / 'results.json').write_text(json.dumps(summary, indent=2) + '\n')
    return 0


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO)
    raise SystemExit(main())
