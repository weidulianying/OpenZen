#include "MainWindow.h"

#include <QApplication>
#include <QStyleFactory>

int main(int argc, char** argv) {
    QApplication::setHighDpiScaleFactorRoundingPolicy(
        Qt::HighDpiScaleFactorRoundingPolicy::PassThrough);

    QApplication app(argc, argv);
    QApplication::setStyle(QStyleFactory::create(QStringLiteral("Fusion")));
    QApplication::setApplicationName(QStringLiteral("OpenZen Loader"));
    QApplication::setOrganizationName(QStringLiteral("OpenZen"));

    loader::MainWindow w;
    w.show();
    return app.exec();
}
