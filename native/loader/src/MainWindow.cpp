#include "MainWindow.h"
#include "loader.h"

#include <QApplication>
#include <QHBoxLayout>
#include <QHeaderView>
#include <QLabel>
#include <QMessageBox>
#include <QString>
#include <QTableWidget>
#include <QTimer>
#include <QVBoxLayout>
#include <QWidget>

#include <string>

namespace loader {

namespace {
QString fromW(const std::wstring& w) {
    return QString::fromWCharArray(w.c_str(), static_cast<int>(w.size()));
}

bool isMinecraft(const std::wstring& title) {
    static const std::wstring prefix = L"Minecraft";
    static const std::wstring banned = L"Launcher";
    if (title.size() < prefix.size()) return false;
    if (title.compare(0, prefix.size(), prefix) != 0) return false;
    return title.find(banned) == std::wstring::npos;
}
} // namespace

MainWindow::MainWindow(QWidget* parent)
        : QMainWindow(parent) {
    styleApp();
    buildUi();

    timer_ = new QTimer(this);
    timer_->setInterval(1000);
    connect(timer_, &QTimer::timeout, this, &MainWindow::refreshNow);
    timer_->start();
    refreshNow();
}

void MainWindow::buildUi() {
    setWindowTitle(QStringLiteral("OpenZen Loader"));
    resize(720, 460);

    auto* central = new QWidget(this);
    auto* layout = new QVBoxLayout(central);
    layout->setContentsMargins(16, 16, 16, 12);
    layout->setSpacing(10);

    auto* title = new QLabel(QStringLiteral("Minecraft Instances"), central);
    title->setObjectName("title");

    hint_ = new QLabel(
        QStringLiteral("Double-click a row to inject. List refreshes every second."),
        central);
    hint_->setObjectName("hint");

    table_ = new QTableWidget(0, 3, central);
    table_->setHorizontalHeaderLabels({
        QStringLiteral("PID"),
        QStringLiteral("Window Title"),
        QStringLiteral("Window Class"),
    });
    table_->verticalHeader()->setVisible(false);
    table_->setSelectionBehavior(QAbstractItemView::SelectRows);
    table_->setSelectionMode(QAbstractItemView::SingleSelection);
    table_->setEditTriggers(QAbstractItemView::NoEditTriggers);
    table_->setAlternatingRowColors(true);
    table_->setShowGrid(false);
    table_->horizontalHeader()->setSectionResizeMode(0, QHeaderView::ResizeToContents);
    table_->horizontalHeader()->setSectionResizeMode(1, QHeaderView::Stretch);
    table_->horizontalHeader()->setSectionResizeMode(2, QHeaderView::ResizeToContents);
    table_->horizontalHeader()->setHighlightSections(false);
    connect(table_, &QTableWidget::cellDoubleClicked,
            this, &MainWindow::onRowDoubleClicked);

    status_ = new QLabel(QStringLiteral("Ready."), central);
    status_->setObjectName("status");
    status_->setWordWrap(true);

    layout->addWidget(title);
    layout->addWidget(hint_);
    layout->addWidget(table_, 1);
    layout->addWidget(status_);
    setCentralWidget(central);
}

void MainWindow::styleApp() {
    // Modern dark palette via QSS - keeps the EXE self-contained (no theme
    // file shipped separately) and looks consistent across Windows versions.
    static const char* kQss = R"qss(
        QMainWindow, QWidget {
            background: #1c1d22;
            color: #e3e5ea;
            font-family: "Segoe UI", "Microsoft YaHei UI", sans-serif;
            font-size: 13px;
        }
        QLabel#title {
            font-size: 18px;
            font-weight: 600;
            color: #ffffff;
        }
        QLabel#hint {
            color: #8a8e98;
            font-size: 12px;
        }
        QLabel#status {
            color: #c2c6cf;
            padding: 6px 10px;
            border: 1px solid #2c2e35;
            border-radius: 6px;
            background: #23252b;
        }
        QTableWidget {
            background: #23252b;
            alternate-background-color: #1f2126;
            gridline-color: #2c2e35;
            border: 1px solid #2c2e35;
            border-radius: 8px;
            selection-background-color: #3d6fd1;
            selection-color: #ffffff;
        }
        QTableWidget::item {
            padding: 6px 8px;
        }
        QHeaderView::section {
            background: #2a2c33;
            color: #cfd2da;
            padding: 6px 10px;
            border: none;
            border-right: 1px solid #1c1d22;
            font-weight: 600;
        }
        QHeaderView::section:last {
            border-right: none;
        }
        QTableCornerButton::section {
            background: #2a2c33;
            border: none;
        }
        QScrollBar:vertical {
            background: #1c1d22;
            width: 10px;
            margin: 0;
        }
        QScrollBar::handle:vertical {
            background: #3a3d45;
            border-radius: 4px;
            min-height: 24px;
        }
        QScrollBar::handle:vertical:hover {
            background: #4a4e58;
        }
        QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {
            height: 0;
        }
    )qss";
    qApp->setStyleSheet(QString::fromUtf8(kQss));
}

void MainWindow::refreshNow() {
    auto procs = list_java_processes();
    QVector<Row> filtered;
    filtered.reserve(procs.size());
    for (const auto& jp : procs) {
        if (!isMinecraft(jp.window_title)) continue;
        Row r{ jp.pid, fromW(jp.window_title), fromW(jp.window_class) };
        filtered.push_back(std::move(r));
    }

    // Preserve selection by pid across refreshes.
    unsigned long selectedPid = 0;
    if (auto sel = table_->currentRow(); sel >= 0 && sel < rows_.size()) {
        selectedPid = rows_[sel].pid;
    }

    rows_ = std::move(filtered);
    table_->setRowCount(rows_.size());
    int restoreRow = -1;
    for (int i = 0; i < rows_.size(); ++i) {
        const auto& r = rows_[i];
        auto pidItem  = new QTableWidgetItem(QString::number(r.pid));
        auto titleItem = new QTableWidgetItem(r.title);
        auto clsItem   = new QTableWidgetItem(r.cls);
        pidItem->setTextAlignment(Qt::AlignCenter);
        table_->setItem(i, 0, pidItem);
        table_->setItem(i, 1, titleItem);
        table_->setItem(i, 2, clsItem);
        if (selectedPid && r.pid == selectedPid) restoreRow = i;
    }
    if (restoreRow >= 0) table_->selectRow(restoreRow);

    status_->setText(QStringLiteral("Watching %1 Minecraft instance(s).")
                     .arg(rows_.size()));
}

void MainWindow::onRowDoubleClicked(int row, int /*column*/) {
    if (row < 0 || row >= rows_.size()) return;
    const auto& r = rows_[row];
    status_->setText(QStringLiteral("Mapping OpenZen.dll into PID %1 (%2)...")
                     .arg(r.pid).arg(r.title));
    QApplication::processEvents();

    std::wstring err = inject(r.pid);
    if (err.empty()) {
        status_->setText(QStringLiteral(
            "Injected into PID %1. Check %TEMP%\\openzen.log for Java side.")
            .arg(r.pid));
    } else {
        QString msg = fromW(err);
        status_->setText(QStringLiteral("Injection failed: %1").arg(msg));
        QMessageBox::critical(this, QStringLiteral("Injection failed"), msg);
    }
}

} // namespace loader
