package me.nallen.modularcodegeneration

import io.kotlintest.specs.StringSpec
import me.nallen.modularcodegeneration.description.Importer

class DescriptionTests : StringSpec() {
    companion object Factory {
        val tests = listOf(
                ImportFile("Hodgkin-Huxley", "CellML", "https://models.physiomeproject.org/workspace/hodgkin_huxley_1952/@@rawfile/a49243bad2797b85ff753ed194435196a2ab1a45/hodgkin_huxley_1952.cellml"),
                ImportFile("Ten Tusscher", "CellML", "https://models.physiomeproject.org/workspace/tentusscher_noble_noble_panfilov_2004/@@rawfile/941ec8e54e46e6fe82765c17f1d47582169baac2/tentusscher_noble_noble_panfilov_2004_a.cellml"),
                ImportFile("Smith-Crampin", "CellML", "https://models.physiomeproject.org/workspace/smith_crampin_2004/@@rawfile/8e72b7911dfe36aee34b355aaa13165964e3d244/smith_crampin_2004.cellml"),
                ImportFile("cardiac_grid", "HAML", "examples/cardiac_grid/main.yaml"),
                ImportFile("heart", "HAML", "examples/heart/main.yaml"),
                ImportFile("nuclear_plant", "HAML", "examples/nuclear_plant/main.yaml"),
                ImportFile("thermostat", "HAML", "examples/thermostat/main.yaml"),
                ImportFile("train_gate", "HAML", "examples/train_gate/main.yaml"),
                ImportFile("water_heater", "HAML", "examples/water_heater/main.yaml")
        )
    }

    init {
        for(test in tests) {
            ("Can Import ${test.name} (${test.format})") {
                Importer.import(test.path)
            }
        }
    }

}

data class ImportFile(
        val name: String,
        val format: String,
        val path: String
)