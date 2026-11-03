package dev.southstar21.kaloria.core.scanner.label

/**
 * ML Kit's lines for a test photo of a drink can's nutrition table (a 330 ml can, the table some
 * 80 degrees round it), shot from above so its rows curve; several names misread.
 */
internal object CanLabel {
    private fun line(text: String, vararg c: Int): OcrLine {
        val corners = listOf(Pt(c[0].toFloat(), c[1].toFloat()), Pt(c[2].toFloat(), c[3].toFloat()), Pt(c[4].toFloat(), c[5].toFloat()), Pt(c[6].toFloat(), c[7].toFloat()))
        val angle = Math.toDegrees(kotlin.math.atan2((c[3] - c[1]).toDouble(), (c[2] - c[0]).toDouble())).toFloat()
        return OcrLine(text, corners.minOf { it.x }.toInt(), corners.minOf { it.y }.toInt(), corners.maxOf { it.x }.toInt(), corners.maxOf { it.y }.toInt(), angle, corners)
    }

    val photo = listOf(
        line("Tápértéktartalom", 188, 980, 525, 1069, 512, 1115, 175, 1026),
        line("100 ml", 195, 1056, 293, 1097, 281, 1125, 183, 1084),
        line("Energja", 194, 1123, 306, 1169, 291, 1204, 179, 1158),
        line("Zsir", 201, 1189, 252, 1210, 240, 1237, 189, 1216),
        line("arnelyböl telitett zsírsavak", 231, 1268, 691, 1388, 676, 1443, 216, 1323),
        line("Szénhidrat", 206, 1316, 363, 1387, 349, 1416, 192, 1345),
        line("amelyböl cukrok", 230, 1393, 492, 1492, 478, 1526, 216, 1427),
        line("Feheie", 223, 1438, 323, 1494, 304, 1526, 204, 1470),
        line("100 ml", 940, 1181, 1077, 1175, 1078, 1210, 941, 1216),
        line("kcal 594.", 989, 1252, 1177, 1226, 1182, 1265, 994, 1291),
        line("180 kJ/42 kJ/ 139 kcal", 753, 1251, 1362, 1146, 1376, 1231, 767, 1336),
        line("0g", 1004, 1322, 1062, 1322, 1062, 1356, 1004, 1356),
        line("0g", 996, 1390, 1054, 1390, 1054, 1424, 996, 1424),
        line("10,6g", 931, 1456, 1045, 1453, 1045, 1490, 931, 1493),
        line("10,6g", 925, 1523, 1037, 1518, 1038, 1553, 926, 1558),
        line("0g", 977, 1585, 1033, 1585, 1033, 1616, 977, 1616),
        line("330 ml", 1273, 1144, 1373, 1112, 1381, 1139, 1281, 1171),
        line("0,02 g", 908, 1652, 1024, 1642, 1026, 1676, 910, 1686),
        line("0g", 1309, 1256, 1345, 1249, 1349, 1278, 1313, 1285),
        line("0g", 1299, 1320, 1338, 1311, 1343, 1338, 1304, 1347),
        line("359", 1268, 1391, 1326, 1369, 1336, 1397, 1278, 1419),
        line("359", 1260, 1453, 1317, 1431, 1326, 1456, 1269, 1478),
        line("0g", 1269, 1505, 1306, 1492, 1313, 1515, 1276, 1528),
        line("O07g", 1216, 1584, 1294, 1545, 1306, 1570, 1228, 1609),
    )
}
