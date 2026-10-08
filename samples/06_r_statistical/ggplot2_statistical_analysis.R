# ==============================================================================
# Jörmungandr R Language & Statistical REPL Test Script
# Tests: R REPL execution, CRAN package imports, ggplot2 plot interception
# ==============================================================================

# 1. Generate Synthetic Observational Data
set.seed(42)
n_samples <- 200

study_data <- data.frame(
  subject_id = sprintf("SUBJ-%04d", 1:n_samples),
  treatment_group = sample(c("Control", "Low-Dose", "High-Dose"), size = n_samples, replace = TRUE),
  baseline_biomarker = rnorm(n_samples, mean = 50, sd = 10),
  exposure_weeks = round(runif(n_samples, min = 2, max = 24))
)

# Response metric correlated with exposure and treatment
study_data$response_metric <- study_data$baseline_biomarker + 
  (study_data$exposure_weeks * 1.8) + 
  ifelse(study_data$treatment_group == "High-Dose", 15, 0) + 
  rnorm(n_samples, mean = 0, sd = 5)

cat(sprintf("Generated %d observational records.\n", nrow(study_data)))
head(study_data, 10)

# 2. Linear Regression Model
fit <- lm(response_metric ~ baseline_biomarker + exposure_weeks + treatment_group, data = study_data)
summary(fit)

# 3. ggplot2 Visual Interception
# When running inside Jörmungandr, ggplot2 plots are intercepted
# and rendered directly in the centralized Scientific Plot Viewer.
if (requireNamespace("ggplot2", quietly = TRUE)) {
  library(ggplot2)
  
  p <- ggplot(study_data, aes(x = exposure_weeks, y = response_metric, color = treatment_group)) +
    geom_point(alpha = 0.7, size = 2.5) +
    geom_smooth(method = "loess", se = TRUE, alpha = 0.2) +
    theme_minimal() +
    labs(
      title = "Dose-Response Trajectory Over Treatment Duration",
      subtitle = "Jörmungandr R Statistical REPL Execution Test",
      x = "Exposure Duration (Weeks)",
      y = "Observed Biomarker Metric",
      color = "Cohort"
    ) +
    scale_color_manual(values = c("Control" = "#64748b", "Low-Dose" = "#38bdf8", "High-Dose" = "#a855f7"))
  
  print(p)
} else {
  # Base R fallback graphic
  plot(study_data$exposure_weeks, study_data$response_metric, 
       col = as.factor(study_data$treatment_group), 
       pch = 19, 
       main = "Base R Dose-Response Scatter Plot",
       xlab = "Weeks", ylab = "Response Metric")
}
