package com.gallakk.pdfmanager.pdf

import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.DrawObject
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.contentstream.operator.state.SetGraphicsStateParameters
import com.tom_roush.pdfbox.contentstream.operator.state.SetMatrix
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlin.math.hypot

/** Estime la résolution effective minimale de chaque image à partir de toutes ses utilisations. */
internal class ImageDpiScanner : PDFStreamEngine() {
    private val minDpi = HashMap<COSStream, Float>()

    init {
        addOperator(Concatenate())
        addOperator(DrawObject())
        addOperator(SetGraphicsStateParameters())
        addOperator(Save())
        addOperator(Restore())
        addOperator(SetMatrix())
    }

    fun dpiOf(image: PDImageXObject): Float = minDpi[image.cosObject] ?: 0f

    override fun processOperator(operator: Operator, operands: List<COSBase>) {
        if (operator.name != "Do") {
            super.processOperator(operator, operands)
            return
        }
        val name = operands.firstOrNull() as? COSName ?: return
        when (val xObject = resources.getXObject(name)) {
            is PDImageXObject -> {
                val ctm = graphicsState.currentTransformationMatrix
                val widthPoints = hypot(ctm.scaleX, ctm.shearY)
                val heightPoints = hypot(ctm.shearX, ctm.scaleY)
                if (widthPoints > 0f && heightPoints > 0f) {
                    val dpiX = xObject.width * 72f / widthPoints
                    val dpiY = xObject.height * 72f / heightPoints
                    val dpi = minOf(dpiX, dpiY)
                    minDpi.merge(xObject.cosObject, dpi) { a, b -> minOf(a, b) }
                }
            }
            is PDFormXObject -> showForm(xObject)
            else -> Unit
        }
    }
}
