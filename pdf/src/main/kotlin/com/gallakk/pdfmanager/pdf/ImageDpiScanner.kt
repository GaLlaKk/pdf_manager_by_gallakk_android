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

/** Estime la résolution effective maximale de chaque image d'une page à partir de la CTM. */
internal class ImageDpiScanner : PDFStreamEngine() {
    private val maxDpi = HashMap<COSStream, Float>()

    init {
        addOperator(Concatenate())
        addOperator(DrawObject())
        addOperator(SetGraphicsStateParameters())
        addOperator(Save())
        addOperator(Restore())
        addOperator(SetMatrix())
    }

    fun dpiOf(image: PDImageXObject): Float = maxDpi[image.cosObject] ?: 0f

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
                if (widthPoints > 0f) {
                    val dpi = xObject.width / (widthPoints / 72f)
                    maxDpi.merge(xObject.cosObject, dpi) { a, b -> maxOf(a, b) }
                }
            }
            is PDFormXObject -> showForm(xObject)
            else -> Unit
        }
    }
}
