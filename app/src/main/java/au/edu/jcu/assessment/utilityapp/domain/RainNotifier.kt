package au.edu.jcu.assessment.utilityapp.domain

interface RainNotifier {
    fun canNotify(): Boolean
    fun showRainReminder(report: WeatherReport): Boolean
    fun cancel()
}
