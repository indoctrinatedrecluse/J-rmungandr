/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jormungandr.dataframe.ml

enum class MlTemplateType(val displayName: String, val category: String) {
    SKLEARN_REGRESSION("Scikit-Learn Regression Pipeline (Ridge & RF)", "Regression"),
    SKLEARN_CLASSIFICATION("Scikit-Learn Classification (Logistic & RF)", "Classification"),
    XGBOOST_GRADIENT_BOOSTING("XGBoost / LightGBM Gradient Boosting", "Ensemble"),
    PYTORCH_NEURAL_NETWORK("PyTorch Deep Learning MLP (PyTorch 2.x)", "Deep Learning"),
    KMEANS_PCA_CLUSTERING("K-Means Clustering & PCA Pipeline", "Unsupervised")
}

/**
 * Library of production Python AI/ML training templates populated dynamically
 * with selected DataFrame columns, target variable, and train/test parameters.
 */
object MlTemplateLibrary {

    fun generateScript(
        templateType: MlTemplateType,
        featureNames: List<String>,
        targetName: String? = "target",
        testSplit: Double = 0.20,
        datasetSource: String = "dataset.csv"
    ): String {
        val featuresListStr = featureNames.joinToString(", ") { "'$it'" }
        val targetCol = targetName ?: "target"

        return when (templateType) {
            MlTemplateType.SKLEARN_REGRESSION -> """
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
from sklearn.linear_model import Ridge
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_squared_error, mean_absolute_error, r2_score

# 1. Load Dataset
df = pd.read_csv('$datasetSource')
features = [$featuresListStr]
target = '$targetCol'

X = df[features].copy()
y = df[target].copy()

# Impute missing values with median
X = X.fillna(X.median())
y = y.fillna(y.median())

# 2. Train / Test Split
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=$testSplit, random_state=42
)

# 3. Model Pipeline Setup (StandardScaler + Ridge & Random Forest)
ridge_pipe = Pipeline([
    ('scaler', StandardScaler()),
    ('regressor', Ridge(alpha=1.0))
])

rf_reg = RandomForestRegressor(n_estimators=100, max_depth=10, random_state=42)

# 4. Train Models
print("Training Ridge Regressor...")
ridge_pipe.fit(X_train, y_train)
y_pred_ridge = ridge_pipe.predict(X_test)

print("Training Random Forest Regressor...")
rf_reg.fit(X_train, y_train)
y_pred_rf = rf_reg.predict(X_test)

# 5. Evaluate Metrics
def evaluate_model(name, y_true, y_pred):
    mse = mean_squared_error(y_true, y_pred)
    rmse = np.sqrt(mse)
    mae = mean_absolute_error(y_true, y_pred)
    r2 = r2_score(y_true, y_pred)
    print(f"=== {name} Metrics ===")
    print(f"  R² Score:  {r2:.4f}")
    print(f"  RMSE:      {rmse:.4f}")
    print(f"  MAE:       {mae:.4f}")

evaluate_model("Ridge", y_test, y_pred_ridge)
evaluate_model("Random Forest", y_test, y_pred_rf)

# 6. Plot Actual vs Predicted & Feature Importances
plt.figure(figsize=(12, 5))
plt.subplot(1, 2, 1)
plt.scatter(y_test, y_pred_rf, alpha=0.6, color='#2563eb', edgecolors='none')
plt.plot([y_test.min(), y_test.max()], [y_test.min(), y_test.max()], 'r--', lw=2, label='Ideal y=x')
plt.title("Actual vs Predicted (Random Forest)")
plt.xlabel("Actual")
plt.ylabel("Predicted")
plt.legend()

plt.subplot(1, 2, 2)
importances = pd.Series(rf_reg.feature_importances_, index=features).sort_values()
importances.plot(kind='barh', color='#059669')
plt.title("Random Forest Feature Importances")
plt.tight_layout()
plt.show()
""".trimIndent()

            MlTemplateType.SKLEARN_CLASSIFICATION -> """
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler, LabelEncoder
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, confusion_matrix, roc_auc_score, roc_curve

# 1. Load Dataset
df = pd.read_csv('$datasetSource')
features = [$featuresListStr]
target = '$targetCol'

X = df[features].copy().fillna(df[features].median())
y = df[target].copy()

# Encode target label if non-numeric
le = LabelEncoder()
y_encoded = le.fit_transform(y.astype(str))

# 2. Train / Test Split
X_train, X_test, y_train, y_test = train_test_split(
    X, y_encoded, test_size=$testSplit, random_state=42, stratify=y_encoded
)

# 3. Scale Features
scaler = StandardScaler()
X_train_scaled = scaler.fit_transform(X_train)
X_test_scaled = scaler.transform(X_test)

# 4. Train Models
clf = RandomForestClassifier(n_estimators=100, max_depth=8, random_state=42)
clf.fit(X_train_scaled, y_train)

y_pred = clf.predict(X_test_scaled)
y_prob = clf.predict_proba(X_test_scaled)

# 5. Classification Report
print("=== Classification Report ===")
print(classification_report(y_test, y_pred, target_names=[str(c) for c in le.classes_]))

# 6. Confusion Matrix & ROC Visualization
cm = confusion_matrix(y_test, y_pred)
plt.figure(figsize=(10, 4))
plt.subplot(1, 2, 1)
sns.heatmap(cm, annot=True, fmt='d', cmap='Blues', xticklabels=le.classes_, yticklabels=le.classes_)
plt.title("Confusion Matrix")
plt.ylabel("Actual Class")
plt.xlabel("Predicted Class")

plt.subplot(1, 2, 2)
if len(le.classes_) == 2:
    fpr, tpr, _ = roc_curve(y_test, y_prob[:, 1])
    auc = roc_auc_score(y_test, y_prob[:, 1])
    plt.plot(fpr, tpr, label=f"ROC (AUC = {auc:.3f})", color='#7c3aed', lw=2)
    plt.plot([0, 1], [0, 1], 'k--', lw=1)
    plt.title("ROC Curve")
    plt.xlabel("False Positive Rate")
    plt.ylabel("True Positive Rate")
    plt.legend()
plt.tight_layout()
plt.show()
""".trimIndent()

            MlTemplateType.XGBOOST_GRADIENT_BOOSTING -> """
import pandas as pd
import numpy as np
import xgboost as xgb
import lightgbm as lgb
from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_squared_error, accuracy_score
import matplotlib.pyplot as plt

# 1. Load Dataset
df = pd.read_csv('$datasetSource')
features = [$featuresListStr]
target = '$targetCol'

X = df[features].copy().fillna(df[features].median())
y = df[target].copy()

X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=$testSplit, random_state=42
)

# 2. Train XGBoost Model
print("Training XGBoost Regressor / Classifier...")
xgb_model = xgb.XGBRegressor(
    n_estimators=200,
    learning_rate=0.05,
    max_depth=6,
    subsample=0.8,
    colsample_bytree=0.8,
    random_state=42
)

eval_set = [(X_train, y_train), (X_test, y_test)]
xgb_model.fit(
    X_train, y_train,
    eval_set=eval_set,
    verbose=False
)

y_pred = xgb_model.predict(X_test)
print(f"XGBoost Test RMSE: {np.sqrt(mean_squared_error(y_test, y_pred)):.4f}")

# 3. Plot Feature Importances
plt.figure(figsize=(10, 5))
xgb.plot_importance(xgb_model, max_num_features=10, height=0.6, color='#0284c7')
plt.title("XGBoost Top Feature Importance")
plt.show()
""".trimIndent()

            MlTemplateType.PYTORCH_NEURAL_NETWORK -> """
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
import matplotlib.pyplot as plt

# 1. Load & Standardize Dataset
df = pd.read_csv('$datasetSource')
features = [$featuresListStr]
target = '$targetCol'

X = df[features].copy().fillna(df[features].mean()).values
y = df[target].copy().values.reshape(-1, 1)

scaler_x = StandardScaler()
X_scaled = scaler_x.fit_transform(X)

X_train, X_test, y_train, y_test = train_test_split(
    X_scaled, y, test_size=$testSplit, random_state=42
)

# 2. PyTorch Dataset & DataLoader
class TabularDataset(Dataset):
    def __init__(self, X, y):
        self.X = torch.tensor(X, dtype=torch.float32)
        self.y = torch.tensor(y, dtype=torch.float32)
    def __len__(self):
        return len(self.X)
    def __getitem__(self, idx):
        return self.X[idx], self.y[idx]

train_loader = DataLoader(TabularDataset(X_train, y_train), batch_size=32, shuffle=True)
test_loader = DataLoader(TabularDataset(X_test, y_test), batch_size=32, shuffle=False)

# 3. Multi-Layer Perceptron (MLP) Architecture
class TabularMLP(nn.Module):
    def __init__(self, in_features):
        super().__init__()
        self.net = nn.Sequential(
            nn.Linear(in_features, 64),
            nn.BatchNorm1d(64),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(64, 32),
            nn.ReLU(),
            nn.Linear(32, 1)
        )
    def forward(self, x):
        return self.net(x)

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
model = TabularMLP(len(features)).to(device)
criterion = nn.MSELoss()
optimizer = torch.optim.Adam(model.parameters(), lr=0.005, weight_decay=1e-4)

# 4. Training Loop
epochs = 50
train_losses = []
for epoch in range(1, epochs + 1):
    model.train()
    running_loss = 0.0
    for batch_x, batch_y in train_loader:
        batch_x, batch_y = batch_x.to(device), batch_y.to(device)
        optimizer.zero_grad()
        preds = model(batch_x)
        loss = criterion(preds, batch_y)
        loss.backward()
        optimizer.step()
        running_loss += loss.item() * batch_x.size(0)
    
    epoch_loss = running_loss / len(train_loader.dataset)
    train_losses.append(epoch_loss)
    if epoch % 10 == 0 or epoch == 1:
        print(f"Epoch [{epoch:02d}/{epochs}] - Loss: {epoch_loss:.4f}")

# 5. Evaluate on Test Set
model.eval()
with torch.no_grad():
    x_test_t = torch.tensor(X_test, dtype=torch.float32).to(device)
    preds = model(x_test_t).cpu().numpy()
    test_mse = np.mean((y_test - preds) ** 2)
    print(f"Final Test MSE: {test_mse:.4f}")

plt.figure(figsize=(8, 4))
plt.plot(train_losses, label="Train Loss (MSE)", color='#e11d48', lw=2)
plt.title("PyTorch Training Loss Convergence")
plt.xlabel("Epoch")
plt.ylabel("Loss")
plt.grid(True, alpha=0.3)
plt.legend()
plt.show()
""".trimIndent()

            MlTemplateType.KMEANS_PCA_CLUSTERING -> """
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from sklearn.preprocessing import StandardScaler
from sklearn.cluster import KMeans
from sklearn.decomposition import PCA
from sklearn.metrics import silhouette_score

# 1. Load Dataset
df = pd.read_csv('$datasetSource')
features = [$featuresListStr]
X = df[features].copy().fillna(df[features].mean())

# 2. Standardize Features
scaler = StandardScaler()
X_scaled = scaler.fit_transform(X)

# 3. Elbow Analysis (k=2 to 8)
inertias = []
k_range = range(2, 9)
for k in k_range:
    km = KMeans(n_clusters=k, random_state=42, n_init='auto')
    km.fit(X_scaled)
    inertias.append(km.inertia_)

# 4. Fit Best K-Means Model (e.g. k=3)
optimal_k = 3
kmeans = KMeans(n_clusters=optimal_k, random_state=42, n_init='auto')
cluster_labels = kmeans.fit_predict(X_scaled)
sil_score = silhouette_score(X_scaled, cluster_labels)
print(f"K-Means (k={optimal_k}) Silhouette Score: {sil_score:.3f}")

# 5. 2D PCA Dimensionality Reduction
pca = PCA(n_components=2)
X_pca = pca.fit_transform(X_scaled)
print(f"PCA Explained Variance Ratio: {pca.explained_variance_ratio_}")

# 6. Visual Cluster Plots
plt.figure(figsize=(12, 5))
plt.subplot(1, 2, 1)
plt.plot(k_range, inertias, 'o-', color='#d97706', lw=2)
plt.title("Elbow Method (Inertia vs k)")
plt.xlabel("Number of Clusters (k)")
plt.ylabel("Inertia (WCSS)")
plt.grid(True, alpha=0.3)

plt.subplot(1, 2, 2)
scatter = plt.scatter(X_pca[:, 0], X_pca[:, 1], c=cluster_labels, cmap='viridis', alpha=0.7)
plt.title(f"2D PCA Projection (Clusters k={optimal_k})")
plt.xlabel(f"PC1 ({pca.explained_variance_ratio_[0]*100:.1f}%)")
plt.ylabel(f"PC2 ({pca.explained_variance_ratio_[1]*100:.1f}%)")
plt.colorbar(scatter, label='Cluster ID')
plt.tight_layout()
plt.show()
""".trimIndent()
        }
    }
}
