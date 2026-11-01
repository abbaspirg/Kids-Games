Attribute VB_Name = "ExportStructureOnly"
' Exports ONLY the structure of the active workbook (sheet names, header text
' from the top rows, and one copy of each distinct formula per column) into a
' NEW workbook. Typed data values (names, IDs, salaries, IBANs...) are NOT copied.
' The company can review the output and share just that, keeping data private.
'
' How to use: open the company Excel file -> Alt+F11 -> File > Import File ->
' choose this .bas -> close editor -> Alt+F8 -> run ExportStructureOnly.
' Then save the new workbook it creates and check it before sharing.

Option Explicit

Private Const HEADER_ROWS As Long = 3   ' top rows of each sheet treated as headers

Public Sub ExportStructureOnly()
    Dim src As Workbook, dst As Workbook, ws As Worksheet, outWs As Worksheet
    Dim c As Range, fCells As Range, topRows As Range
    Dim r As Long, seen As Object, key As String

    Set src = ActiveWorkbook
    Set dst = Workbooks.Add
    Set outWs = dst.Worksheets(1)
    outWs.Name = "Structure"
    outWs.Range("A1:E1").Value = Array("Sheet", "Cell", "Type", "Header text / Formula", "Used range")
    outWs.Range("A1:E1").Font.Bold = True
    r = 2

    Application.ScreenUpdating = False
    For Each ws In src.Worksheets
        ' sheet summary row
        outWs.Cells(r, 1).Value = ws.Name
        outWs.Cells(r, 3).Value = "Sheet"
        outWs.Cells(r, 5).Value = ws.UsedRange.Address(False, False)
        r = r + 1

        ' header text in the top rows of the used range
        Set topRows = ws.UsedRange.Rows(1).Resize(HEADER_ROWS)
        For Each c In topRows.Cells
            If Not c.HasFormula And VarType(c.Value) = vbString Then
                If Len(c.Value) > 0 Then
                    outWs.Cells(r, 1).Value = ws.Name
                    outWs.Cells(r, 2).Value = c.Address(False, False)
                    outWs.Cells(r, 3).Value = "Header"
                    outWs.Cells(r, 4).Value = "'" & c.Value
                    r = r + 1
                End If
            End If
        Next c

        ' one example of each distinct formula per column
        Set fCells = Nothing
        On Error Resume Next
        Set fCells = ws.UsedRange.SpecialCells(xlCellTypeFormulas)
        On Error GoTo 0
        If Not fCells Is Nothing Then
            Set seen = CreateObject("Scripting.Dictionary")
            For Each c In fCells.Cells
                key = c.Column & "|" & c.FormulaR1C1
                If Not seen.Exists(key) Then
                    seen.Add key, True
                    outWs.Cells(r, 1).Value = ws.Name
                    outWs.Cells(r, 2).Value = c.Address(False, False)
                    outWs.Cells(r, 3).Value = "Formula"
                    outWs.Cells(r, 4).Value = "'" & c.Formula
                    r = r + 1
                End If
            Next c
        End If
    Next ws
    Application.ScreenUpdating = True

    outWs.Columns("A:C").AutoFit
    outWs.Columns("D").ColumnWidth = 100
    outWs.Columns("E").AutoFit
    MsgBox "Done: " & (r - 2) & " rows exported." & vbCrLf & _
           "No typed data was copied. Please still read it once before sharing " & _
           "(header rows or formulas could contain a name).", vbInformation
End Sub
