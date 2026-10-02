#!/usr/bin/env python3
"""Re-run NAV-ACC-20261001 against current source; writes only a self-cleaning /tmp directory.

Requires JDK 17, the frozen cached dependencies, and an existing core/storage classes.jar
used only for linking scanner-null ViewModel probes. Exit 1 means a tested assertion failed.
"""
from __future__ import annotations

import argparse
import logging
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", type=Path, required=True, help="Path to a JDK 17 java executable")
    parser.add_argument("--cache", type=Path, default=Path.home() / ".gradle/caches/modules-2/files-2.1")
    args = parser.parse_args()
    if not args.java.is_file():
        parser.error("--java must be an existing executable")
    repo = Path(__file__).resolve().parents[4]
    evidence = Path(__file__).resolve().parent
    scratch = Path(tempfile.mkdtemp(prefix="leaflet-navigation-recheck-", dir="/tmp"))
    try:
        def jar(group: str, artifact: str, version: str) -> str:
            matches = list((args.cache / group / artifact / version).glob("*/*.jar"))
            if len(matches) != 1:
                raise RuntimeError(f"Expected one cached JAR: {group}:{artifact}:{version}")
            return str(matches[0])

        def aar(group: str, artifact: str, version: str) -> str:
            matches = list((args.cache / group / artifact / version).glob("*/*.aar"))
            if len(matches) != 1:
                raise RuntimeError(f"Expected one cached AAR: {group}:{artifact}:{version}")
            destination = scratch / (artifact + ".jar")
            with zipfile.ZipFile(matches[0]) as archive:
                destination.write_bytes(archive.read("classes.jar"))
            return str(destination)

        stdlib = jar("org.jetbrains.kotlin", "kotlin-stdlib", "2.0.21")
        coroutines = jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.10.2")
        annotations = jar("org.jetbrains", "annotations", "13.0")
        compiler = [jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.0.21"), stdlib,
                    jar("org.jetbrains.kotlin", "kotlin-script-runtime", "2.0.21"),
                    jar("org.jetbrains.intellij.deps", "trove4j", "1.0.20200330"), coroutines, annotations]
        storage = repo / "core/storage/build/intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar"
        if not storage.is_file():
            raise RuntimeError("Missing compiled core/storage link dependency; build it in an unrestricted environment first")
        shutil.copyfile(storage, scratch / "storage-dependency.jar")
        classpath = [stdlib, coroutines, annotations,
                     jar("org.jetbrains.kotlinx", "kotlinx-coroutines-test-jvm", "1.10.2"),
                     jar("junit", "junit", "4.13.2"), jar("org.hamcrest", "hamcrest-core", "1.3"),
                     aar("androidx.lifecycle", "lifecycle-viewmodel-android", "2.8.7"),
                     aar("androidx.lifecycle", "lifecycle-viewmodel-ktx", "2.8.7"),
                     jar("androidx.lifecycle", "lifecycle-common-jvm", "2.8.7"), str(scratch / "storage-dependency.jar")]
        sources = [repo / "core/model/src/main/kotlin/io/leaflet/model" / name
                   for name in ["Ids.kt", "Document.kt", "Library.kt"]]
        sources += [repo / "app/src/main/kotlin/io/leaflet/reader/ui/library" / name
                    for name in ["BrowseLevel.kt", "Breadcrumbs.kt", "LibraryViewModel.kt"]]
        sources += [repo / "app/src/test/kotlin/io/leaflet/reader/ui/library" / name
                    for name in ["BreadcrumbTest.kt", "LibraryAncestorNavigationTest.kt", "LibraryBrowseSelectionTest.kt"]]
        sources += [evidence / "NavigationAcceptanceProbe.kt"]
        compile_command = [str(args.java), "-Xmx1g", "-cp", ":".join(compiler),
                           "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect",
                           "-jvm-target", "17", "-classpath", ":".join(classpath), "-d", str(scratch / "classes"),
                           *map(str, sources)]
        compiled = subprocess.run(compile_command, check=False)
        if compiled.returncode:
            return compiled.returncode
        baseline = ["BreadcrumbTest", "LibraryAncestorNavigationTest", "LibraryBrowseSelectionTest"]
        status = 0
        for names in [baseline, ["NavigationAcceptanceProbe"]]:
            command = [str(args.java), "-cp", str(scratch / "classes") + ":" + ":".join(classpath),
                       "org.junit.runner.JUnitCore", *["io.leaflet.reader.ui.library." + name for name in names]]
            result = subprocess.run(command, check=False)
            if result.returncode:
                status = result.returncode
        return status
    except (OSError, RuntimeError, zipfile.BadZipFile, KeyError) as error:
        logging.error("Acceptance recheck could not execute: %s", error)
        return 2
    finally:
        shutil.rmtree(scratch)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    raise SystemExit(main())
