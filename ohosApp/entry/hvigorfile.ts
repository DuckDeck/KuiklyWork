import { hapTasks } from '@ohos/hvigor-ohos-plugin';
import { kuiklyCompilePlugin, kuiklyCopyAssetsPlugin } from 'kuikly-ohos-compile-plugin';
import { execFileSync } from 'child_process';
import * as path from 'path';

function buildKuiklySharedLib() {
    const projectRoot = path.resolve(__dirname, '..', '..');
    const scriptPath = path.join(projectRoot, 'scripts', 'build-ohos-shared.js');
    execFileSync(process.execPath, [scriptPath], {
        cwd: projectRoot,
        stdio: 'inherit'
    });
}

if (!process.argv.some((arg) => arg === '--sync' || arg.startsWith('--sync='))) {
    buildKuiklySharedLib();
}

export default {
    system: hapTasks,  /* Built-in plugin of Hvigor. It cannot be modified. */
    plugins:[kuiklyCompilePlugin()]         /* Custom plugin to extend the functionality of Hvigor. */
}
